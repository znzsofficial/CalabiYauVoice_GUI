<script lang="ts">
  let {
    pages = [] as Array<number | '...'>,
    currentPage = 1,
    totalPages = 0,
    onGoPage = (_page: number) => {},
  }: {
    /** 页码模型：数字为可点击页，'...' 为省略占位 */
    pages?: Array<number | '...'>;
    currentPage?: number;
    totalPages?: number;
    onGoPage?: (page: number) => void;
  } = $props();
</script>

<nav class="pagination" aria-label="分页">
  <button class="page-btn" disabled={currentPage <= 1} aria-label="上一页" onclick={() => onGoPage(currentPage - 1)}>‹</button>
  {#each pages as page, i (i)}
    {#if page === '...'}
      <span class="page-ellipsis" aria-hidden="true">…</span>
    {:else}
      <button
        class="page-btn"
        class:active={page === currentPage}
        aria-current={page === currentPage ? 'page' : undefined}
        aria-label={`第 ${page} 页`}
        onclick={() => onGoPage(page)}
      >{page}</button>
    {/if}
  {/each}
  <button class="page-btn" disabled={currentPage >= totalPages} aria-label="下一页" onclick={() => onGoPage(currentPage + 1)}>›</button>
</nav>
