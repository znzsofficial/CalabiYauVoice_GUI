export async function generateZip() {
  const { default: JSZip } = await import('jszip');
  return new JSZip();
}
