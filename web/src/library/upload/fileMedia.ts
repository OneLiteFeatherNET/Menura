// The duration and resolution of a file, read by the browser from the file's own media headers. Where the browser
// cannot read a file (an unsupported container, for example MKV in some browsers), the facts stay empty and the
// editor types them in. The server never probes media (principle 2).

import { formatOf, kindForFormat } from './declaration';

export interface MediaFacts {
  durationSeconds?: number;
  width?: number;
  height?: number;
}

export function readMediaFacts(file: File): Promise<MediaFacts> {
  const url = URL.createObjectURL(file);
  const finish = (facts: MediaFacts, resolve: (value: MediaFacts) => void) => {
    URL.revokeObjectURL(url);
    resolve(facts);
  };
  return new Promise((resolve) => {
    if (kindForFormat(formatOf(file.name)) === 'image') {
      const image = new Image();
      image.onload = () => finish({ width: image.naturalWidth, height: image.naturalHeight }, resolve);
      image.onerror = () => finish({}, resolve);
      image.src = url;
      return;
    }
    const video = document.createElement('video');
    video.preload = 'metadata';
    video.onloadedmetadata = () =>
      finish(
        {
          durationSeconds: Number.isFinite(video.duration) && video.duration >= 1 ? Math.round(video.duration) : undefined,
          width: video.videoWidth || undefined,
          height: video.videoHeight || undefined,
        },
        resolve,
      );
    video.onerror = () => finish({}, resolve);
    video.src = url;
  });
}
