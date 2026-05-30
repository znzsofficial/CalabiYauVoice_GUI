import { httpErrorMessage, type CategoryFile } from './searchApi';
import { fileNameFromTitle } from './utils';

export { fileNameFromTitle };

export function downloadBlob(blob: Blob, name: string): void {
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
}

export function uniqueFileName(name: string, usedNames: Set<string>): string {
  if (!usedNames.has(name)) {
    usedNames.add(name);
    return name;
  }

  const dotIndex = name.lastIndexOf('.');
  const base = dotIndex > 0 ? name.slice(0, dotIndex) : name;
  const ext = dotIndex > 0 ? name.slice(dotIndex) : '';
  let index = 2;
  let nextName = `${base}-${index}${ext}`;
  while (usedNames.has(nextName)) {
    index += 1;
    nextName = `${base}-${index}${ext}`;
  }
  usedNames.add(nextName);
  return nextName;
}

export async function downloadFilesInParallel(
  files: CategoryFile[],
  concurrency: number,
  onProgress: (finished: number) => void
): Promise<Array<{ name: string; blob: Blob }>> {
  const output: Array<{ name: string; blob: Blob }> = [];
  let nextIndex = 0;
  let finished = 0;

  async function worker(): Promise<void> {
    while (nextIndex < files.length) {
      const file = files[nextIndex++];
      const response = await fetch(`/api/file-download?url=${encodeURIComponent(file.url)}`);
      if (!response.ok) throw new Error(httpErrorMessage(response.status));
      output.push({ name: file.name, blob: await response.blob() });
      finished += 1;
      onProgress(finished);
    }
  }

  await Promise.all(Array.from({ length: Math.min(concurrency, files.length) }, () => worker()));
  return output;
}

export async function generateZip() {
  const { default: JSZip } = await import('jszip');
  return new JSZip();
}
