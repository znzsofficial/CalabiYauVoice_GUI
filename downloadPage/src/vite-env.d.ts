/// <reference types="vite/client" />

declare module 'gifenc' {
  export type GifPalette = number[][];

  export interface GifEncoder {
    writeFrame(
      index: Uint8Array,
      width: number,
      height: number,
      options?: {
        palette?: GifPalette;
        delay?: number;
        repeat?: number;
        transparent?: boolean;
        transparentIndex?: number;
        dispose?: number;
        first?: boolean;
      }
    ): void;
    finish(): void;
    bytes(): Uint8Array<ArrayBuffer>;
    bytesView(): Uint8Array<ArrayBuffer>;
  }

  export function GIFEncoder(options?: { auto?: boolean }): GifEncoder;
  export function quantize(data: Uint8ClampedArray | Uint8Array, maxColors: number, options?: { format?: string }): GifPalette;
  export function applyPalette(data: Uint8ClampedArray | Uint8Array, palette: GifPalette, format?: string): Uint8Array<ArrayBuffer>;
}
