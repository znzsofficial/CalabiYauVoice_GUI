import { fileNameFromTitle } from '../utils';

export { fileNameFromTitle };

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
