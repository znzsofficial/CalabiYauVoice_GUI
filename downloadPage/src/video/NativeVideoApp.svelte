<script lang="ts">
  import { GIFEncoder, applyPalette, quantize } from 'gifenc';
  import JSZip from 'jszip';
  import { onDestroy } from 'svelte';

  type Status = 'idle' | 'loading' | 'done' | 'error';
  type ExportFormat = 'image/png' | 'image/jpeg' | 'image/webp';
  type CropRatio = 'source' | '16:9' | '1:1' | '9:16';
  type ToolTab = 'single' | 'batch' | 'gif';

  let file = $state<File | null>(null);
  let videoUrl = $state('');
  let previewUrl = $state('');
  let duration = $state(0);
  let position = $state(0);
  let exportFormat = $state<ExportFormat>('image/png');
  let cropRatio = $state<CropRatio>('source');
  let batchCount = $state(8);
  let sheetColumns = $state(4);
  let gifSeconds = $state(2);
  let gifFps = $state(8);
  let gifWidth = $state(480);
  let batchBusy = $state(false);
  let activeTool = $state<ToolTab>('single');
  let seekerHoverTime = $state(0);
  let seekerHoverPercent = $state(0);
  let seekerHovering = $state(false);
  let status = $state<Status>('idle');
  let message = $state('');
  let videoEl = $state<HTMLVideoElement | null>(null);
  let canvasEl = $state<HTMLCanvasElement | null>(null);
  let seekTimer: ReturnType<typeof setTimeout> | null = null;

  const nativeIdeas = [
    '连续截图：按固定间隔生成 PNG/JPEG/WebP 素材',
    '音频波形：为视频片段生成节奏预览',
    '素材信息看板：汇总时长、分辨率、文件大小与类型',
    '封面套版：组合标题、徽标、水印与基础形状',
    '快捷预设：保存常用格式、比例、帧数与 GIF 参数'
  ];

  const formatOptions: { value: ExportFormat; label: string; extension: string }[] = [
    { value: 'image/png', label: 'PNG', extension: 'png' },
    { value: 'image/jpeg', label: 'JPEG', extension: 'jpg' },
    { value: 'image/webp', label: 'WebP', extension: 'webp' }
  ];

  const ratioOptions: { value: CropRatio; label: string }[] = [
    { value: 'source', label: '原始比例' },
    { value: '16:9', label: '16:9 封面' },
    { value: '1:1', label: '1:1 方图' },
    { value: '9:16', label: '9:16 竖图' }
  ];

  const progressPercent = $derived(duration > 0 ? Math.max(0, Math.min(position / duration * 100, 100)) : 0);
  const batchMarkers = $derived(duration > 0 && activeTool === 'batch' ? getSampleTimes(batchCount).map(time => time / duration * 100) : []);

  function revoke(url: string): void {
    if (url) URL.revokeObjectURL(url);
  }

  function chooseFile(event: Event): void {
    const next = (event.currentTarget as HTMLInputElement).files?.[0] || null;
    setFile(next);
    (event.currentTarget as HTMLInputElement).value = '';
  }

  function setFile(next: File | null): void {
    file = next;
    duration = 0;
    position = 0;
    status = next ? 'loading' : 'idle';
    message = next ? '正在整理素材信息' : '';
    revoke(videoUrl);
    revoke(previewUrl);
    previewUrl = '';
    videoUrl = next ? URL.createObjectURL(next) : '';
  }

  function onDrop(event: DragEvent): void {
    event.preventDefault();
    const next = event.dataTransfer?.files?.[0] || null;
    if (next) setFile(next);
  }

  function onVideoLoaded(): void {
    duration = Number.isFinite(videoEl?.duration || 0) ? videoEl?.duration || 0 : 0;
    status = duration > 0 ? 'idle' : 'error';
    message = duration > 0 ? '拖动时间轴选择画面' : '素材信息准备中';
    seekFrame(0, 0);
  }

  function seekFrame(value: number, delay = 100): void {
    position = Math.max(0, Math.min(value, duration || value));
    if (seekTimer) clearTimeout(seekTimer);
    seekTimer = setTimeout(() => {
      if (videoEl) videoEl.currentTime = position;
    }, delay);
  }

  function stepFrame(delta: number): void {
    seekFrame(position + delta, 0);
  }

  function updateSeekerHover(event: PointerEvent): void {
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const percent = Math.max(0, Math.min((event.clientX - rect.left) / rect.width, 1));
    seekerHoverPercent = percent * 100;
    seekerHoverTime = percent * (duration || 0);
    seekerHovering = true;
  }

  function onVideoSeeked(): void {
    if (!batchBusy) void updatePreview();
  }

  function getTargetSize(sourceWidth: number, sourceHeight: number): { width: number; height: number } {
    if (cropRatio === 'source') return { width: sourceWidth, height: sourceHeight };
    const [ratioWidth, ratioHeight] = cropRatio.split(':').map(Number);
    const sourceRatio = sourceWidth / sourceHeight;
    const targetRatio = ratioWidth / ratioHeight;
    if (sourceRatio > targetRatio) {
      return { width: Math.round(sourceHeight * targetRatio), height: sourceHeight };
    }
    return { width: sourceWidth, height: Math.round(sourceWidth / targetRatio) };
  }

  function blobFromCanvas(canvas: HTMLCanvasElement, type: ExportFormat): Promise<Blob | null> {
    const quality = type === 'image/png' ? undefined : 0.92;
    return new Promise(resolve => canvas.toBlob(resolve, type, quality));
  }

  function drawCurrentFrame(outputWidth?: number): CanvasRenderingContext2D | null {
    if (!videoEl || !canvasEl || videoEl.readyState < 2) return null;
    const sourceWidth = videoEl.videoWidth;
    const sourceHeight = videoEl.videoHeight;
    if (!sourceWidth || !sourceHeight) return null;
    const { width, height } = getTargetSize(sourceWidth, sourceHeight);
    const sourceX = Math.max(0, Math.round((sourceWidth - width) / 2));
    const sourceY = Math.max(0, Math.round((sourceHeight - height) / 2));
    const targetWidth = outputWidth ? Math.max(64, Math.min(Math.round(outputWidth), width)) : width;
    const targetHeight = Math.max(1, Math.round(targetWidth * height / width));
    canvasEl.width = targetWidth;
    canvasEl.height = targetHeight;
    const context = canvasEl.getContext('2d');
    if (!context) return null;
    context.drawImage(videoEl, sourceX, sourceY, width, height, 0, 0, targetWidth, targetHeight);
    return context;
  }

  async function captureFrame(type: ExportFormat = exportFormat): Promise<Blob | null> {
    if (!canvasEl || !drawCurrentFrame()) return null;
    return blobFromCanvas(canvasEl, type);
  }

  async function updatePreview(): Promise<void> {
    const blob = await captureFrame();
    if (!blob) return;
    revoke(previewUrl);
    previewUrl = URL.createObjectURL(blob);
  }

  function getExtension(type = exportFormat): string {
    return formatOptions.find(option => option.value === type)?.extension || 'png';
  }

  function getFrameName(index: number, time: number, extension = getExtension()): string {
    const ratioSuffix = cropRatio === 'source' ? 'source' : cropRatio.replace(':', 'x');
    return `frame_${String(index + 1).padStart(3, '0')}_${Math.round(time * 1000)}ms_${ratioSuffix}.${extension}`;
  }

  function downloadBlob(blob: Blob, name: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    link.click();
    URL.revokeObjectURL(url);
  }

  function getSampleTimes(count: number): number[] {
    const safeCount = Math.max(1, Math.min(Math.round(count), 60));
    if (!duration || safeCount === 1) return [Math.min(position, duration || position)];
    const end = Math.max(0, duration - 0.08);
    const step = end / safeCount;
    return Array.from({ length: safeCount }, (_, index) => Math.min(end, step * index + step / 2));
  }

  function seekTo(time: number): Promise<void> {
    if (!videoEl) return Promise.resolve();
    if (seekTimer) clearTimeout(seekTimer);
    position = Math.max(0, Math.min(time, duration || time));
    return new Promise((resolve, reject) => {
      if (!videoEl) {
        resolve();
        return;
      }
      const timeout = window.setTimeout(() => {
        cleanup();
        reject(new Error('seek timeout'));
      }, 5000);
      const cleanup = (): void => {
        window.clearTimeout(timeout);
        videoEl?.removeEventListener('seeked', onSeeked);
        videoEl?.removeEventListener('error', onError);
      };
      const onSeeked = (): void => {
        cleanup();
        resolve();
      };
      const onError = (): void => {
        cleanup();
        reject(new Error('seek failed'));
      };
      videoEl.addEventListener('seeked', onSeeked, { once: true });
      videoEl.addEventListener('error', onError, { once: true });
      videoEl.currentTime = position;
    });
  }

  async function exportFrame(): Promise<void> {
    if (!file) return;
    const blob = await captureFrame();
    if (!blob) {
      status = 'error';
      message = '当前画面正在准备';
      return;
    }
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    const extension = getExtension();
    const ratioSuffix = cropRatio === 'source' ? 'source' : cropRatio.replace(':', 'x');
    const name = `${file.name.replace(/\.[^.]+$/, '') || 'video'}_${Math.round(position * 1000)}ms_${ratioSuffix}.${extension}`;
    link.href = url;
    link.download = name;
    link.click();
    URL.revokeObjectURL(url);
    status = 'done';
    message = `已导出 ${name}`;
  }

  async function captureBatch(): Promise<{ blob: Blob; time: number }[]> {
    if (!file || !videoEl) return [];
    const times = getSampleTimes(batchCount);
    const frames: { blob: Blob; time: number }[] = [];
    batchBusy = true;
    status = 'loading';
    try {
      for (const [index, time] of times.entries()) {
        message = `正在抽帧 ${index + 1}/${times.length}`;
        await seekTo(time);
        const blob = await captureFrame();
        if (!blob) throw new Error('capture failed');
        frames.push({ blob, time });
      }
      return frames;
    } finally {
      batchBusy = false;
      void updatePreview();
    }
  }

  async function exportBatchZip(): Promise<void> {
    if (!file) return;
    try {
      const frames = await captureBatch();
      if (!frames.length) throw new Error('empty batch');
      const zip = new JSZip();
      const extension = getExtension();
      frames.forEach(({ blob, time }, index) => {
        zip.file(getFrameName(index, time, extension), blob);
      });
      message = '正在生成 ZIP';
      const zipBlob = await zip.generateAsync({ type: 'blob' });
      const name = `${file.name.replace(/\.[^.]+$/, '') || 'video'}_${frames.length}_frames.zip`;
      downloadBlob(zipBlob, name);
      status = 'done';
      message = `已导出 ${name}`;
    } catch {
      status = 'error';
      message = '批量抽帧已暂停，可调整数量后再次生成';
      batchBusy = false;
    }
  }

  async function exportContactSheet(): Promise<void> {
    if (!file || !canvasEl) return;
    try {
      const frames = await captureBatch();
      if (!frames.length) throw new Error('empty sheet');
      const bitmaps = await Promise.all(frames.map(frame => createImageBitmap(frame.blob)));
      const columns = Math.max(1, Math.min(Math.round(sheetColumns), bitmaps.length));
      const rows = Math.ceil(bitmaps.length / columns);
      const cellWidth = bitmaps[0].width;
      const cellHeight = bitmaps[0].height;
      canvasEl.width = cellWidth * columns;
      canvasEl.height = cellHeight * rows;
      const context = canvasEl.getContext('2d');
      if (!context) throw new Error('no canvas context');
      context.fillStyle = '#09090b';
      context.fillRect(0, 0, canvasEl.width, canvasEl.height);
      bitmaps.forEach((bitmap, index) => {
        const x = (index % columns) * cellWidth;
        const y = Math.floor(index / columns) * cellHeight;
        context.drawImage(bitmap, x, y, cellWidth, cellHeight);
        bitmap.close();
      });
      const blob = await blobFromCanvas(canvasEl, exportFormat);
      if (!blob) throw new Error('sheet export failed');
      const name = `${file.name.replace(/\.[^.]+$/, '') || 'video'}_contact_sheet.${getExtension()}`;
      downloadBlob(blob, name);
      status = 'done';
      message = `已导出 ${name}`;
    } catch {
      status = 'error';
      message = '联系表生成已暂停，可重新开始一次';
      batchBusy = false;
    }
  }

  async function exportGif(): Promise<void> {
    if (!file || !videoEl || !canvasEl) return;
    const seconds = Math.max(0.5, Math.min(Number(gifSeconds) || 2, 6));
    const fps = Math.max(2, Math.min(Math.round(Number(gifFps) || 8), 15));
    const width = Math.max(96, Math.min(Math.round(Number(gifWidth) || 480), 960));
    const frameCount = Math.max(1, Math.min(Math.round(seconds * fps), 90));
    const start = Math.max(0, Math.min(position, duration || position));
    const lastTime = Math.max(0, (duration || start) - 0.08);
    const gif = GIFEncoder();
    batchBusy = true;
    status = 'loading';
    try {
      for (let index = 0; index < frameCount; index += 1) {
        const time = Math.min(lastTime, start + index / fps);
        message = `正在编码 GIF ${index + 1}/${frameCount}`;
        await seekTo(time);
        const context = drawCurrentFrame(width);
        if (!context) throw new Error('gif frame failed');
        const { data } = context.getImageData(0, 0, canvasEl.width, canvasEl.height);
        const format = 'rgb444';
        const palette = quantize(data, 256, { format });
        const indexedFrame = applyPalette(data, palette, format);
        gif.writeFrame(indexedFrame, canvasEl.width, canvasEl.height, {
          palette,
          delay: Math.round(1000 / fps),
          repeat: 0
        });
        await new Promise(resolve => window.setTimeout(resolve, 0));
      }
      gif.finish();
      const blob = new Blob([gif.bytes()], { type: 'image/gif' });
      const name = `${file.name.replace(/\.[^.]+$/, '') || 'video'}_${Math.round(start * 1000)}ms_${seconds}s_${fps}fps.gif`;
      downloadBlob(blob, name);
      status = 'done';
      message = `已导出 ${name}`;
    } catch {
      status = 'error';
      message = 'GIF 生成已暂停，可调整参数后再次生成';
    } finally {
      batchBusy = false;
      void updatePreview();
    }
  }

  async function copyFrame(): Promise<void> {
    const blob = await captureFrame('image/png');
    if (!blob) {
      status = 'error';
      message = '当前画面正在准备';
      return;
    }
    if (!navigator.clipboard || typeof ClipboardItem === 'undefined') {
      status = 'error';
      message = '剪贴板图片写入由系统接管，可先导出图片';
      return;
    }
    try {
      await navigator.clipboard.write([new ClipboardItem({ [blob.type]: blob })]);
      status = 'done';
      message = '已复制当前帧到剪贴板';
    } catch {
      status = 'error';
      message = '剪贴板授权待确认，可先导出图片';
    }
  }

  function setExportFormat(event: Event): void {
    exportFormat = (event.currentTarget as HTMLSelectElement).value as ExportFormat;
    void updatePreview();
  }

  function setCropRatio(event: Event): void {
    cropRatio = (event.currentTarget as HTMLSelectElement).value as CropRatio;
    void updatePreview();
  }

  function formatTime(seconds: number): string {
    const safe = Math.max(0, seconds || 0);
    const minutes = Math.floor(safe / 60);
    const secs = Math.floor(safe % 60);
    const millis = Math.floor((safe % 1) * 1000);
    return `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}.${String(millis).padStart(3, '0')}`;
  }

  function formatFileSize(bytes: number): string {
    if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let index = 0;
    while (size >= 1024 && index < units.length - 1) {
      size /= 1024;
      index += 1;
    }
    return `${size.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
  }

  onDestroy(() => {
    if (seekTimer) clearTimeout(seekTimer);
    revoke(videoUrl);
    revoke(previewUrl);
  });
</script>

<svelte:head>
  <meta property="og:image" content="/icon.svg">
</svelte:head>

<header class="header">
  <div class="header-content">
    <a href="/" class="header-back" aria-label="返回首页"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg></a>
    <h1 class="header-title"><svg class="header-logo" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="10" r="2"/><path d="m21 15-5-5L5 19"/></svg>视频素材工坊</h1>
    <a class="header-link" href="/search/">Wiki 搜索</a>
  </div>
</header>

<main class="main native-video-page">
  {#if !videoUrl}
    <section class="empty-workbench">
      <div class="empty-copy">
        <strong>把视频整理成可用素材</strong>
        <span>提取截图、制作封面、生成短 GIF、打包帧图，并输出缩略图联系表。</span>
      </div>
      <label class="file-drop hero-drop" ondragover={(event) => event.preventDefault()} ondrop={onDrop}>
        <input type="file" accept="video/*,.mp4,.webm,.mov,.m4v,.ogv" onchange={chooseFile}>
        <strong>拖入或选择视频素材</strong>
        <span>开始整理截图、封面、GIF、ZIP 帧包与联系表</span>
      </label>
    </section>
  {:else}
    <section class="workspace-shell">
      {#if message}<div class:error={status === 'error'} class:done={status === 'done'} class:loading={status === 'loading'} class="status-bar"><span>{message}</span></div>{/if}

      <div class="workspace-grid">
        <section class="visual-area">
          <div class="video-shell">
            <!-- svelte-ignore a11y_media_has_caption -->
            <video bind:this={videoEl} src={videoUrl} controls preload="metadata" muted playsinline onloadedmetadata={onVideoLoaded} onseeked={onVideoSeeked}></video>
            <div class="mini-preview">
              {#if previewUrl}<img src={previewUrl} alt="当前帧预览">{:else}<span>画面预览</span>{/if}
            </div>
          </div>

          <div class="timeline-card">
            <div class="timeline-meta">
              <span>{formatTime(position)}</span>
              <span>{formatTime(duration)}</span>
            </div>
            <div
              class="custom-range-container"
              style={`--progress: ${progressPercent}%; --hover-x: ${seekerHoverPercent}%;`}
            >
              {#if seekerHovering}
                <span class="hover-time">{formatTime(seekerHoverTime)}</span>
              {/if}
              {#if batchMarkers.length}
                <div class="marker-track" aria-hidden="true">
                  {#each batchMarkers as marker}
                    <span style={`left: ${marker}%;`}></span>
                  {/each}
                </div>
              {/if}
              <input type="range" min="0" max={Math.max(duration, 1)} step="0.04" value={position} onpointermove={updateSeekerHover} onpointerleave={() => seekerHovering = false} oninput={(event) => seekFrame(Number((event.currentTarget as HTMLInputElement).value))}>
            </div>
            <div class="frame-stepper">
              <button type="button" onclick={() => stepFrame(-0.04)} disabled={batchBusy}>-1 帧</button>
              <button type="button" onclick={() => stepFrame(0.04)} disabled={batchBusy}>+1 帧</button>
            </div>
          </div>
        </section>

        <aside class="toolbox-area">
          <label class="file-drop compact-drop" ondragover={(event) => event.preventDefault()} ondrop={onDrop}>
            <input type="file" accept="video/*,.mp4,.webm,.mov,.m4v,.ogv" onchange={chooseFile}>
            <strong>{file?.name || '视频素材'}</strong>
            <span>点击更换素材</span>
          </label>

          {#if file}
            <div class="file-meta">
              <span>{formatFileSize(file.size)}</span>
              <span>{file.type || '素材类型'}</span>
              <span>{duration > 0 ? formatTime(duration) : '整理时长'}</span>
            </div>
          {/if}

          <div class:single={activeTool === 'single'} class:batch={activeTool === 'batch'} class:gif={activeTool === 'gif'} class="tabs-header" role="tablist" aria-label="视频素材工具">
            <button type="button" class:active={activeTool === 'single'} onclick={() => activeTool = 'single'}>单帧</button>
            <button type="button" class:active={activeTool === 'batch'} onclick={() => activeTool = 'batch'}>批量</button>
            <button type="button" class:active={activeTool === 'gif'} onclick={() => activeTool = 'gif'}>GIF</button>
          </div>

          <div class="tab-content">
            {#if activeTool === 'single'}
              <div class="section-head"><strong>单帧素材</strong><span>当前时间点的画面输出</span></div>
              <div class="options-grid">
                <label>
                  <span>导出格式</span>
                  <select value={exportFormat} onchange={setExportFormat}>
                    {#each formatOptions as option}
                      <option value={option.value}>{option.label}</option>
                    {/each}
                  </select>
                </label>
                <label>
                  <span>封面比例</span>
                  <select value={cropRatio} onchange={setCropRatio}>
                    {#each ratioOptions as option}
                      <option value={option.value}>{option.label}</option>
                    {/each}
                  </select>
                </label>
              </div>
              <div class="actions sticky-footer-actions">
                <button type="button" onclick={updatePreview} disabled={batchBusy}>刷新预览</button>
                <button type="button" onclick={copyFrame} disabled={status === 'loading' || batchBusy}>复制当前帧</button>
                <button class="primary" type="button" onclick={exportFrame} disabled={status === 'loading' || batchBusy}>导出当前帧</button>
              </div>
            {:else if activeTool === 'batch'}
              <div class="section-head"><strong>批量素材</strong><span>帧图包与缩略图联系表</span></div>
              <div class="options-grid">
                <label>
                  <span>导出格式</span>
                  <select value={exportFormat} onchange={setExportFormat}>
                    {#each formatOptions as option}
                      <option value={option.value}>{option.label}</option>
                    {/each}
                  </select>
                </label>
                <label>
                  <span>封面比例</span>
                  <select value={cropRatio} onchange={setCropRatio}>
                    {#each ratioOptions as option}
                      <option value={option.value}>{option.label}</option>
                    {/each}
                  </select>
                </label>
                <label>
                  <span>抽帧数量</span>
                  <input type="number" min="1" max="60" bind:value={batchCount}>
                </label>
                <label>
                  <span>联系表列数</span>
                  <input type="number" min="1" max="12" bind:value={sheetColumns}>
                </label>
              </div>
              <div class="actions sticky-footer-actions">
                <button type="button" onclick={exportContactSheet} disabled={status === 'loading' || batchBusy}>导出联系表</button>
                <button class="primary" type="button" onclick={exportBatchZip} disabled={status === 'loading' || batchBusy}>导出 ZIP 帧包</button>
              </div>
            {:else}
              <div class="section-head"><strong>短 GIF</strong><span>当前时间开始的动图片段</span></div>
              <div class="options-grid">
                <label>
                  <span>封面比例</span>
                  <select value={cropRatio} onchange={setCropRatio}>
                    {#each ratioOptions as option}
                      <option value={option.value}>{option.label}</option>
                    {/each}
                  </select>
                </label>
                <label>
                  <span>时长（秒）</span>
                  <input type="number" min="0.5" max="6" step="0.5" bind:value={gifSeconds}>
                </label>
                <label>
                  <span>帧率（FPS）</span>
                  <input type="number" min="2" max="15" bind:value={gifFps}>
                </label>
                <label>
                  <span>宽度（px）</span>
                  <input type="number" min="96" max="960" step="16" bind:value={gifWidth}>
                </label>
              </div>
              <div class="actions sticky-footer-actions">
                <button class="primary" type="button" onclick={exportGif} disabled={status === 'loading' || batchBusy}>导出 GIF</button>
              </div>
            {/if}
          </div>
        </aside>
      </div>
    </section>
  {/if}

  <section class="ideas-card">
    <div class="section-head"><strong>更多素材玩法</strong><span>围绕截图、动图和素材整理继续扩展</span></div>
    <div class="idea-grid">
      {#each nativeIdeas as idea}
        <span>{idea}</span>
      {/each}
    </div>
  </section>

  <canvas bind:this={canvasEl} class="hidden-canvas"></canvas>
</main>
