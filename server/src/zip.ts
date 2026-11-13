/**
 * A streaming ZIP writer with no compression (JPEGs don't shrink anyway). Each file streams straight from
 * R2 to the client; its CRC and size go in a data descriptor after it, so nothing is held in memory.
 */

const TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

export function crc32(data: Uint8Array, crc = 0): number {
  let c = ~crc >>> 0;
  for (let i = 0; i < data.length; i++) c = TABLE[(c ^ data[i]) & 0xff] ^ (c >>> 8);
  return ~c >>> 0;
}

export interface ZipEntry {
  name: string;
  /** Opens the file's bytes when it's its turn; null skips it. */
  open: () => Promise<ReadableStream<Uint8Array> | null>;
}

/** MS-DOS time and date for the entry headers. */
function dosTime(d: Date): [number, number] {
  const time = (d.getUTCHours() << 11) | (d.getUTCMinutes() << 5) | (d.getUTCSeconds() >> 1);
  const date = ((Math.max(d.getUTCFullYear(), 1980) - 1980) << 9) | ((d.getUTCMonth() + 1) << 5) | d.getUTCDate();
  return [time, date];
}

const FLAGS = 0x0808; // bit 3: sizes in a data descriptor; bit 11: names are UTF-8

function localHeader(name: Uint8Array, time: number, date: number): Uint8Array {
  const b = new Uint8Array(30 + name.length);
  const v = new DataView(b.buffer);
  v.setUint32(0, 0x04034b50, true);
  v.setUint16(4, 20, true);
  v.setUint16(6, FLAGS, true);
  v.setUint16(8, 0, true); // stored
  v.setUint16(10, time, true);
  v.setUint16(12, date, true);
  // crc and sizes are 0 here; they follow the data
  v.setUint16(26, name.length, true);
  b.set(name, 30);
  return b;
}

function descriptor(crc: number, size: number): Uint8Array {
  const b = new Uint8Array(16);
  const v = new DataView(b.buffer);
  v.setUint32(0, 0x08074b50, true);
  v.setUint32(4, crc, true);
  v.setUint32(8, size, true);
  v.setUint32(12, size, true);
  return b;
}

interface Written { name: Uint8Array; crc: number; size: number; offset: number }

function centralEntry(e: Written, time: number, date: number): Uint8Array {
  const b = new Uint8Array(46 + e.name.length);
  const v = new DataView(b.buffer);
  v.setUint32(0, 0x02014b50, true);
  v.setUint16(4, 20, true);
  v.setUint16(6, 20, true);
  v.setUint16(8, FLAGS, true);
  v.setUint16(10, 0, true);
  v.setUint16(12, time, true);
  v.setUint16(14, date, true);
  v.setUint32(16, e.crc, true);
  v.setUint32(20, e.size, true);
  v.setUint32(24, e.size, true);
  v.setUint16(28, e.name.length, true);
  v.setUint32(42, e.offset, true);
  b.set(e.name, 46);
  return b;
}

function endRecord(count: number, size: number, offset: number): Uint8Array {
  const b = new Uint8Array(22);
  const v = new DataView(b.buffer);
  v.setUint32(0, 0x06054b50, true);
  v.setUint16(8, count, true);
  v.setUint16(10, count, true);
  v.setUint32(12, size, true);
  v.setUint32(16, offset, true);
  return b;
}

/** The whole archive as a stream. Files are read one at a time, in order. */
export function zipStream(entries: ZipEntry[], at = new Date()): ReadableStream<Uint8Array> {
  const [time, date] = dosTime(at);
  const enc = new TextEncoder();
  const { readable, writable } = new TransformStream<Uint8Array, Uint8Array>();
  (async () => {
    const w = writable.getWriter();
    const done: Written[] = [];
    let offset = 0;
    const put = async (b: Uint8Array) => {
      await w.write(b);
      offset += b.length;
    };
    try {
      for (const e of entries) {
        const body = await e.open();
        if (!body) continue;
        const name = enc.encode(e.name);
        const start = offset;
        await put(localHeader(name, time, date));
        let crc = 0;
        let size = 0;
        const r = body.getReader();
        for (;;) {
          const { value, done: end } = await r.read();
          if (end) break;
          crc = crc32(value, crc);
          size += value.length;
          await put(value);
        }
        await put(descriptor(crc, size));
        done.push({ name, crc, size, offset: start });
      }
      const centralStart = offset;
      for (const e of done) await put(centralEntry(e, time, date));
      await put(endRecord(done.length, offset - centralStart, centralStart));
      await w.close();
    } catch (err) {
      await w.abort(err);
    }
  })();
  return readable;
}

/** "Front steps", 3, "after" → "03 Front steps - after.jpg", safe on every OS. */
export function photoName(index: number, label: string | undefined, side: "before" | "after"): string {
  const clean = (label ?? "").replace(/[\\/:*?"<>|\u0000-\u001f]+/g, " ").replace(/\s+/g, " ").trim().slice(0, 60);
  return `${String(index).padStart(2, "0")}${clean ? ` ${clean}` : ""} - ${side}.jpg`;
}
