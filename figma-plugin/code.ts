// Figma plugin main code
// Connects UI to MCP bridge server via /plugin websocket through the UI iframe

figma.showUI(__html__, { width: 360, height: 220 });

// Send initial hello info to UI so it can include it in the WebSocket hello
(function sendInit() {
  try {
    const fileKey = (figma as any).fileKey ?? null;
    const pluginVersion = '1.0.0';
    const capabilities = ['get_selection', 'replace_text'];
    figma.ui.postMessage({ type: 'init', fileKey, pluginVersion, capabilities });
  } catch (e) {
    // no-op
  }
})();


/*
 * Figma MCP — TypeScript handlers
 * Drop-in handlers converted from your JS utilities. Compatible with documentAccess: "dynamic-page".
 *
 * Conventions:
 * - Args objects are typed; errors are normalized via buildError(...)
 * - All node lookups use figma.getNodeByIdAsync(...)
 * - For TEXT changes we load fonts via setCharacters helper (handles mixed fonts)
 * - Some functions reference sendProgressUpdate / generateCommandId — declare them in your codebase
 */

// External progress hooks (declare or implement on your side)
declare function sendProgressUpdate(
    commandId: string,
    name: string,
    state: 'started' | 'in_progress' | 'completed' | 'error',
    progress: number,
    total: number,
    completed: number,
    message: string,
    extra?: Record<string, any> | null
): void;

declare function generateCommandId(): string;

// ──────────────────────────────────────────────────────────────────────────────
// Error helpers
// ──────────────────────────────────────────────────────────────────────────────
function buildError(code: number, message: string, data?: any) {
  return { code, message, data };
}

function normalizeError(err: any) {
  return err && typeof err === 'object' && 'code' in err
      ? err
      : buildError(2999, String(err?.message ?? err));
}

// Common error codes
const ERR = {
  INVALID_ARGS: 2000,
  NOT_FOUND: 2001,
  WRONG_TYPE: 2002,
  OP_FAILED: 2003,
  UNKNOWN_CMD: 2004,
};

// ──────────────────────────────────────────────────────────────────────────────
// Types
// ──────────────────────────────────────────────────────────────────────────────
type ColorRGBA = { r: number; g: number; b: number; a?: number };

interface CreateRectangleParams {
  x?: number; y?: number; width?: number; height?: number; name?: string; parentId?: string;
}
interface CreateFrameParams extends CreateRectangleParams {
  fillColor?: ColorRGBA;
  strokeColor?: ColorRGBA;
  strokeWeight?: number;
  layoutMode?: 'NONE' | 'HORIZONTAL' | 'VERTICAL';
  layoutWrap?: 'NO_WRAP' | 'WRAP';
  paddingTop?: number; paddingRight?: number; paddingBottom?: number; paddingLeft?: number;
  primaryAxisAlignItems?: 'MIN' | 'CENTER' | 'MAX' | 'SPACE_BETWEEN';
  counterAxisAlignItems?: 'MIN' | 'CENTER' | 'MAX';
  layoutSizingHorizontal?: 'FIXED' | 'HUG' | 'FILL';
  layoutSizingVertical?: 'FIXED' | 'HUG' | 'FILL';
  itemSpacing?: number;
}
interface CreateTextParams {
  x?: number; y?: number; text?: string; fontSize?: number; fontWeight?: number;
  fontColor?: ColorRGBA; name?: string; parentId?: string;
}

interface ExportImageParams { nodeId: string; scale?: number; format?: 'PNG'|'JPG'|'SVG'|'PDF' };
interface MoveNodeParams { nodeId: string; x: number; y: number }
interface ResizeNodeParams { nodeId: string; width: number; height: number }
interface DeleteNodeParams { nodeId: string }
interface SetFillParams { nodeId: string; color: ColorRGBA }
interface SetStrokeParams { nodeId: string; color: ColorRGBA; weight?: number }
interface CornerRadiusParams { nodeId: string; radius: number; corners?: [boolean,boolean,boolean,boolean] }
interface TextContentParams { nodeId: string; text: string }
interface CloneNodeParams { nodeId: string; x?: number; y?: number }
interface ScanTextNodesParams { nodeId: string; useChunking?: boolean; chunkSize?: number; commandId?: string }
interface BatchTextReplacementParams { nodeId: string; text: Array<{ nodeId: string; text: string }>; commandId?: string }

// ──────────────────────────────────────────────────────────────────────────────
// Utilities (ported from your JS)
// ──────────────────────────────────────────────────────────────────────────────
function rgbaToHex(color: ColorRGBA) {
  const r = Math.round(color.r * 255);
  const g = Math.round(color.g * 255);
  const b = Math.round(color.b * 255);
  const a = color.a !== undefined ? Math.round(color.a * 255) : 255;
  const hex = (x: number) => {
    const s = x.toString(16);
    return s.length < 2 ? '0' + s : s;
  };
  if (a === 255) return `#${hex(r)}${hex(g)}${hex(b)}`;
  return `#${hex(r)}${hex(g)}${hex(b)}${hex(a)}`;
}

function filterFigmaNode(node: any): any {
  if (node.type === 'VECTOR') return null;
  const filtered: any = { id: node.id, name: node.name, type: node.type };

  if (node.fills && node.fills.length > 0) {
    filtered.fills = node.fills.map((fill: any) => {
      const processedFill: any = { ...fill };
      delete processedFill.boundVariables;
      delete processedFill.imageRef;
      if (processedFill.gradientStops) {
        processedFill.gradientStops = processedFill.gradientStops.map((stop: any) => {
          const s: any = { ...stop };
          if (s.color) s.color = rgbaToHex(s.color);
          delete s.boundVariables;
          return s;
        });
      }
      if (processedFill.color) processedFill.color = rgbaToHex(processedFill.color);
      return processedFill;
    });
  }

  if (node.strokes && node.strokes.length > 0) {
    filtered.strokes = node.strokes.map((stroke: any) => {
      const s: any = { ...stroke };
      delete s.boundVariables;
      if (s.color) s.color = rgbaToHex(s.color);
      return s;
    });
  }

  if (node.cornerRadius !== undefined) filtered.cornerRadius = node.cornerRadius;
  if (node.absoluteBoundingBox) filtered.absoluteBoundingBox = node.absoluteBoundingBox;
  if (node.characters) filtered.characters = node.characters;

  if (node.style) {
    filtered.style = {
      fontFamily: node.style.fontFamily,
      fontStyle: node.style.fontStyle,
      fontWeight: node.style.fontWeight,
      fontSize: node.style.fontSize,
      textAlignHorizontal: node.style.textAlignHorizontal,
      letterSpacing: node.style.letterSpacing,
      lineHeightPx: node.style.lineHeightPx,
    };
  }

  if (node.children) {
    filtered.children = node.children
        .map((child: any) => filterFigmaNode(child))
        .filter((c: any) => c !== null);
  }
  return filtered;
}

function customBase64Encode(bytes: Uint8Array) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  let base64 = '';
  const byteLength = bytes.byteLength;
  const byteRemainder = byteLength % 3;
  const mainLength = byteLength - byteRemainder;
  let a: number, b: number, c: number, d: number, chunk: number;
  for (let i = 0; i < mainLength; i += 3) {
    chunk = (bytes[i] << 16) | (bytes[i + 1] << 8) | bytes[i + 2];
    a = (chunk & 0b111111000000000000000000) >> 18;
    b = (chunk & 0b000000111111000000000000) >> 12;
    c = (chunk & 0b000000000000111111000000) >> 6;
    d = (chunk & 0b000000000000000000111111);
    base64 += chars[a] + chars[b] + chars[c] + chars[d];
  }
  if (byteRemainder === 1) {
    chunk = bytes[mainLength];
    a = (chunk & 0b11111100) >> 2;
    b = (chunk & 0b00000011) << 4;
    base64 += chars[a] + chars[b] + '==';
  } else if (byteRemainder === 2) {
    chunk = (bytes[mainLength] << 8) | bytes[mainLength + 1];
    a = (chunk & 0b1111110000000000) >> 10;
    b = (chunk & 0b0000001111110000) >> 4;
    c = (chunk & 0b0000000000001111) << 2;
    base64 += chars[a] + chars[b] + chars[c] + '=';
  }
  return base64;
}

const delay = (ms: number) => new Promise<void>((res) => setTimeout(res, ms));

function uniqBy<T>(arr: T[], predicate: ((o: T) => any) | keyof T) {
  const cb = typeof predicate === 'function' ? predicate : (o: any) => o[predicate];
  return [...(arr.reduce((map, item) => { const key = item == null ? item : cb(item); if (!map.has(key)) map.set(key, item); return map; }, new Map()).values())];
}

// Helpers for text font handling (ported)
const setCharacters = async (
    node: TextNode,
    characters: string,
    options?: { fallbackFont?: FontName; smartStrategy?: 'prevail' | 'strict' | 'experimental' }
) => {
  const fallbackFont: FontName = options?.fallbackFont ?? { family: 'Inter', style: 'Regular' };
  try {
    if (node.fontName === figma.mixed) {
      if (options?.smartStrategy === 'prevail') {
        const fontHashTree: Record<string, number> = {};
        for (let i = 1; i < node.characters.length; i++) {
          const charFont = node.getRangeFontName(i - 1, i) as FontName;
          const key = `${charFont.family}::${charFont.style}`;
          fontHashTree[key] = (fontHashTree[key] ?? 0) + 1;
        }
        const prevailed = Object.keys(fontHashTree).map((k) => [k, fontHashTree[k]] as [string, number]).sort((a, b) => b[1] - a[1])[0];
        const [family, style] = prevailed[0].split('::');
        const prevailedFont: FontName = { family, style };
        await figma.loadFontAsync(prevailedFont);
        node.fontName = prevailedFont;
      } else if (options?.smartStrategy === 'strict') {
        return setCharactersWithStrictMatchFont(node, characters, fallbackFont);
      } else if (options?.smartStrategy === 'experimental') {
        return setCharactersWithSmartMatchFont(node, characters, fallbackFont);
      } else {
        const firstCharFont = node.getRangeFontName(0, 1) as FontName;
        await figma.loadFontAsync(firstCharFont);
        node.fontName = firstCharFont;
      }
    } else {
      await figma.loadFontAsync(node.fontName as FontName);
    }
  } catch (err) {
    console.warn(`Failed to load "${(node.fontName as any)['family']} ${(node.fontName as any)['style']}"; using fallback`, err);
    await figma.loadFontAsync(fallbackFont);
    node.fontName = fallbackFont;
  }
  try {
    node.characters = characters;
    return true;
  } catch (err) {
    console.warn('Failed to set characters. Skipped.', err);
    return false;
  }
};

const setCharactersWithStrictMatchFont = async (node: TextNode, characters: string, fallbackFont: FontName) => {
  const fontHashTree: Record<string, string> = {};
  for (let i = 1; i < node.characters.length; i++) {
    const startIdx = i - 1;
    const startCharFont = node.getRangeFontName(startIdx, i) as FontName;
    const startVal = `${startCharFont.family}::${startCharFont.style}`;
    while (i < node.characters.length) {
      i++;
      const charFont = node.getRangeFontName(i - 1, i) as FontName;
      if (startVal !== `${charFont.family}::${charFont.style}`) break;
    }
    fontHashTree[`${startIdx}_${i}`] = startVal;
  }
  await figma.loadFontAsync(fallbackFont);
  node.fontName = fallbackFont;
  node.characters = characters;
  await Promise.all(
      Object.keys(fontHashTree).map(async (range) => {
        const [start, end] = range.split('_');
        const [family, style] = fontHashTree[range].split('::');
        const matchedFont: FontName = { family, style };
        await figma.loadFontAsync(matchedFont);
        return node.setRangeFontName(Number(start), Number(end), matchedFont);
      })
  );
  return true;
};

const getDelimiterPos = (str: string, delimiter: string, startIdx = 0, endIdx = str.length) => {
  const indices: Array<[number, number]> = [];
  let temp = startIdx;
  for (let i = startIdx; i < endIdx; i++) {
    if (str[i] === delimiter && i + startIdx !== endIdx && temp !== i + startIdx) {
      indices.push([temp, i + startIdx]);
      temp = i + startIdx + 1;
    }
  }
  if (temp !== endIdx) indices.push([temp, endIdx]);
  return indices.filter(Boolean);
};

const buildLinearOrder = (node: TextNode) => {
  const fontTree: Array<{ start: number; delimiter: string; family: string; style: string }> = [];
  const newLinesPos = getDelimiterPos(node.characters, '\n');
  newLinesPos.forEach(([start, end]) => {
    const font = node.getRangeFontName(start, end) as any;
    if (font === figma.mixed) {
      const spacesPos = getDelimiterPos(node.characters, ' ', start, end);
      spacesPos.forEach(([sStart, sEnd]) => {
        const sFont: any = node.getRangeFontName(sStart, sEnd);
        if (sFont === figma.mixed) {
          const sFont2: any = node.getRangeFontName(sStart, (sStart as any)[0]);
          fontTree.push({ start: sStart, delimiter: ' ', family: sFont2.family, style: sFont2.style });
        } else {
          fontTree.push({ start: sStart, delimiter: ' ', family: sFont.family, style: sFont.style });
        }
      });
    } else {
      fontTree.push({ start, delimiter: '\n', family: font.family, style: font.style });
    }
  });
  return fontTree.sort((a, b) => a.start - b.start).map(({ family, style, delimiter }) => ({ family, style, delimiter }));
};

const setCharactersWithSmartMatchFont = async (node: TextNode, characters: string, fallbackFont: FontName) => {
  const rangeTree = buildLinearOrder(node);
  const fontsToLoad = uniqBy(rangeTree, ({ family, style }) => `${family}::${style}`).map(({ family, style }) => ({ family, style }));
  await Promise.all([...fontsToLoad, fallbackFont].map(figma.loadFontAsync));
  node.fontName = fallbackFont;
  node.characters = characters;
  let prevPos = 0;
  rangeTree.forEach(({ family, style, delimiter }) => {
    if (prevPos < node.characters.length) {
      const delPos = node.characters.indexOf(delimiter, prevPos);
      const endPos = delPos > prevPos ? delPos : node.characters.length;
      const matchedFont: FontName = { family, style };
      node.setRangeFontName(prevPos, endPos, matchedFont);
      prevPos = endPos + 1;
    }
  });
  return true;
};
function getTextDecoder():
    | { decode(input?: ArrayBufferView | ArrayBuffer): string }
    | null {
  const TD = (globalThis as any).TextDecoder;
  if (!TD) return null;
  try { return new TD('utf-8'); } catch { try { return new TD(); } catch { return null; } }
}

function utf8Decode(bytes: Uint8Array): string {
  const td = getTextDecoder();
  if (td) return td.decode(bytes);

  // Фолбэк: ручной UTF-8 декодер
  let out = '', i = 0;
  while (i < bytes.length) {
    const c = bytes[i++];
    if (c < 128) out += String.fromCharCode(c);
    else if (c < 224) { const c2 = bytes[i++]; out += String.fromCharCode(((c & 31) << 6) | (c2 & 63)); }
    else if (c < 240) { const c2 = bytes[i++], c3 = bytes[i++]; out += String.fromCharCode(((c & 15) << 12) | ((c2 & 63) << 6) | (c3 & 63)); }
    else { const c2 = bytes[i++], c3 = bytes[i++], c4 = bytes[i++];
      const cp = ((c & 7) << 18) | ((c2 & 63) << 12) | ((c3 & 63) << 6) | (c4 & 63);
      const off = cp - 0x10000;
      out += String.fromCharCode(0xD800 + (off >> 10), 0xDC00 + (off & 0x3FF));
    }
  }
  return out;
}

// Export JSON helper: some environments return Uint8Array, try to parse JSON if so
async function exportNodeAsJsonRestV1(node: ExportMixin): Promise<any> {
  const data: any = await (node as any).exportAsync({ format: 'JSON_REST_V1' as any });

  if (data instanceof Uint8Array) {
    try {
      const json = utf8Decode(data);
      return JSON.parse(json);
    } catch (e: any) {
      throw buildError(ERR.OP_FAILED, `Failed to parse JSON_REST_V1: ${e?.message ?? e}`);
    }
  }

  if (typeof data === 'string') {
    try { return JSON.parse(data); } catch {}
  }

  return data; // уже объект
}

// ──────────────────────────────────────────────────────────────────────────────
// Handlers
// ──────────────────────────────────────────────────────────────────────────────
async function handleGetDocumentInfo() {
  try {
    await figma.currentPage.loadAsync();
    const page = figma.currentPage;
    return {
      name: page.name,
      id: page.id,
      type: page.type,
      children: page.children.map((node) => ({ id: node.id, name: (node as any).name, type: node.type })),
      currentPage: { id: page.id, name: page.name, childCount: page.children.length },
      pages: [{ id: page.id, name: page.name, childCount: page.children.length }],
    };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleGetSelection() {
  try {
    return {
      selectionCount: figma.currentPage.selection.length,
      selection: figma.currentPage.selection.map((node) => ({
        id: node.id,
        name: (node as any).name,
        type: node.type,
        visible: (node as any).visible,
      })),
    };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleGetNodeInfo(args: { nodeId?: string }) {
  if (!args || typeof args.nodeId !== 'string') throw buildError(ERR.INVALID_ARGS, 'Expected { nodeId: string }');
  try {
    const node = await figma.getNodeByIdAsync(args.nodeId);
    if (!node) throw buildError(ERR.NOT_FOUND, `Node not found with ID: ${args.nodeId}`);
    const response = await exportNodeAsJsonRestV1(node as any);
    return filterFigmaNode(response.document);
  } catch (e: any) { throw normalizeError(e); }
}

async function handleGetNodesInfo(args: { nodeIds?: string[] }) {
  if (!args || !Array.isArray(args.nodeIds)) throw buildError(ERR.INVALID_ARGS, 'Expected { nodeIds: string[] }');
  try {
    const nodes = await Promise.all(args.nodeIds.map((id) => figma.getNodeByIdAsync(id)));
    const valid = nodes.filter((n): n is SceneNode => n !== null) as SceneNode[];
    const responses = await Promise.all(valid.map(async (node) => {
      const resp = await exportNodeAsJsonRestV1(node as any);
      return { nodeId: node.id, document: filterFigmaNode(resp.document) };
    }));
    return responses;
  } catch (e: any) { throw normalizeError(e); }
}

async function handleGetReactions(args: { nodeIds?: string[] }) {
  if (!args || !Array.isArray(args.nodeIds)) throw buildError(ERR.INVALID_ARGS, 'Expected { nodeIds: string[] }');
  try {
    const commandId = generateCommandId();
    sendProgressUpdate(commandId, 'get_reactions', 'started', 0, args.nodeIds.length, 0, `Starting deep search for reactions in ${args.nodeIds.length} nodes and their children`);

    async function highlightNodeWithAnimation(node: any) {
      const originalStrokeWeight = (node as any).strokeWeight;
      const originalStrokes = (node as any).strokes ? [ ...(node as any).strokes ] : [];
      try {
        (node as any).strokeWeight = 4;
        (node as any).strokes = [{ type: 'SOLID', color: { r: 1, g: 0.5, b: 0 }, opacity: 0.8 }];
        setTimeout(() => {
          try { (node as any).strokeWeight = originalStrokeWeight; (node as any).strokes = originalStrokes; } catch {}
        }, 1500);
      } catch {}
    }

    function getNodePath(node: any) {
      const path: string[] = [];
      let current: any = node;
      while (current && current.parent) { path.unshift(current.name); current = current.parent; }
      return path.join(' > ');
    }

    async function findNodesWithReactions(node: any, processed = new Set<string>(), depth = 0, results: any[] = []) {
      if (processed.has(node.id)) return results; processed.add(node.id);
      let filteredReactions: any[] = [];
      if ((node as any).reactions && (node as any).reactions.length > 0) {
        filteredReactions = (node as any).reactions.filter((r: any) => {
          if (r.action && r.action.navigation === 'CHANGE_TO') return false;
          if (Array.isArray(r.actions)) return !r.actions.some((a: any) => a.navigation === 'CHANGE_TO');
          return true;
        });
      }
      if (filteredReactions.length > 0) {
        results.push({ id: node.id, name: (node as any).name, type: node.type, depth, hasReactions: true, reactions: filteredReactions, path: getNodePath(node) });
        await highlightNodeWithAnimation(node);
      }
      if ('children' in node && (node as any).children) {
        for (const child of (node as any).children) await findNodesWithReactions(child, processed, depth + 1, results);
      }
      return results;
    }

    let allResults: any[] = []; let processedCount = 0; const total = args.nodeIds.length;
    for (const nodeId of args.nodeIds) {
      try {
        const node = await figma.getNodeByIdAsync(nodeId);
        if (!node) { processedCount++; sendProgressUpdate(commandId, 'get_reactions', 'in_progress', processedCount/total, total, processedCount, `Node not found: ${nodeId}`); continue; }
        const results = await findNodesWithReactions(node);
        allResults = allResults.concat(results);
        processedCount++;
        sendProgressUpdate(commandId, 'get_reactions', 'in_progress', processedCount/total, total, processedCount, `Processed node ${processedCount}/${total}, found ${results.length} nodes with reactions`);
      } catch (error: any) {
        processedCount++;
        sendProgressUpdate(commandId, 'get_reactions', 'in_progress', processedCount/total, total, processedCount, `Error processing node: ${error.message}`);
      }
    }

    sendProgressUpdate(commandId, 'get_reactions', 'completed', 1, total, total, `Completed deep search: found ${allResults.length} nodes with reactions.`);
    return { nodesCount: total, nodesWithReactions: allResults.length, nodes: allResults };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleReadMyDesign() {
  try {
    const nodes = await Promise.all(figma.currentPage.selection.map((n) => figma.getNodeByIdAsync(n.id)));
    const valid = nodes.filter((n): n is SceneNode => n !== null) as SceneNode[];
    const responses = await Promise.all(valid.map(async (node) => {
      const resp = await exportNodeAsJsonRestV1(node as any);
      return { nodeId: node.id, document: filterFigmaNode(resp.document) };
    }));
    return responses;
  } catch (e: any) { throw normalizeError(e); }
}

async function handleCreateRectangle(params: CreateRectangleParams = {}) {
  try {
    const { x = 0, y = 0, width = 100, height = 100, name = 'Rectangle', parentId } = params;
    const rect = figma.createRectangle();
    rect.x = x; rect.y = y; rect.resize(width, height); rect.name = name;
    if (parentId) {
      const parent = await figma.getNodeByIdAsync(parentId);
      if (!parent) throw buildError(ERR.NOT_FOUND, `Parent node not found: ${parentId}`);
      if (!('appendChild' in parent)) throw buildError(ERR.WRONG_TYPE, `Parent cannot contain children: ${parentId}`);
      (parent as BaseNode & ChildrenMixin).appendChild(rect);
    } else { figma.currentPage.appendChild(rect); }
    return { id: rect.id, name: rect.name, x: rect.x, y: rect.y, width: rect.width, height: rect.height, parentId: rect.parent ? rect.parent.id : undefined };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleCreateFrame(params: CreateFrameParams = {}) {
  try {
    const {
      x = 0, y = 0, width = 100, height = 100, name = 'Frame', parentId,
      fillColor, strokeColor, strokeWeight,
      layoutMode = 'NONE', layoutWrap = 'NO_WRAP',
      paddingTop = 10, paddingRight = 10, paddingBottom = 10, paddingLeft = 10,
      primaryAxisAlignItems = 'MIN', counterAxisAlignItems = 'MIN',
      layoutSizingHorizontal = 'FIXED', layoutSizingVertical = 'FIXED',
      itemSpacing = 0
    } = params;

    const frame = figma.createFrame();
    frame.x = x; frame.y = y; frame.resize(width, height); frame.name = name;

    if (layoutMode !== 'NONE') {
      frame.layoutMode = layoutMode as any;
      (frame as any).layoutWrap = layoutWrap;
      frame.paddingTop = paddingTop; frame.paddingRight = paddingRight; frame.paddingBottom = paddingBottom; frame.paddingLeft = paddingLeft;
      frame.primaryAxisAlignItems = primaryAxisAlignItems as any;
      frame.counterAxisAlignItems = counterAxisAlignItems as any;
      (frame as any).layoutSizingHorizontal = layoutSizingHorizontal;
      (frame as any).layoutSizingVertical = layoutSizingVertical;
      frame.itemSpacing = itemSpacing;
    }

    if (fillColor) {
      frame.fills = [{ type: 'SOLID', color: { r: +fillColor.r || 0, g: +fillColor.g || 0, b: +fillColor.b || 0 }, opacity: +(fillColor.a ?? 1) } as SolidPaint];
    }
    if (strokeColor) {
      frame.strokes = [{ type: 'SOLID', color: { r: +strokeColor.r || 0, g: +strokeColor.g || 0, b: +strokeColor.b || 0 }, opacity: +(strokeColor.a ?? 1) } as SolidPaint];
    }
    if (strokeWeight !== undefined) (frame as any).strokeWeight = strokeWeight;

    if (parentId) {
      const parent = await figma.getNodeByIdAsync(parentId);
      if (!parent) throw buildError(ERR.NOT_FOUND, `Parent node not found: ${parentId}`);
      if (!('appendChild' in parent)) throw buildError(ERR.WRONG_TYPE, `Parent cannot contain children: ${parentId}`);
      (parent as BaseNode & ChildrenMixin).appendChild(frame);
    } else { figma.currentPage.appendChild(frame); }

    return {
      id: frame.id, name: frame.name, x: frame.x, y: frame.y, width: frame.width, height: frame.height,
      fills: frame.fills, strokes: frame.strokes, strokeWeight: (frame as any).strokeWeight,
      layoutMode: frame.layoutMode, layoutWrap: (frame as any).layoutWrap, parentId: frame.parent ? frame.parent.id : undefined,
    };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleCreateText(params: CreateTextParams = {}) {
  try {
    const { x = 0, y = 0, text = 'Text', fontSize = 14, fontWeight = 400, fontColor = { r: 0, g: 0, b: 0, a: 1 }, name = '', parentId } = params;
    const getFontStyle = (weight: number) => ({ 100: 'Thin', 200: 'Extra Light', 300: 'Light', 400: 'Regular', 500: 'Medium', 600: 'Semi Bold', 700: 'Bold', 800: 'Extra Bold', 900: 'Black' } as const)[weight] ?? 'Regular';
    const node = figma.createText(); node.x = x; node.y = y; node.name = name || text;
    try {
      const fontName: FontName = { family: 'Inter', style: getFontStyle(fontWeight) as any };
      await figma.loadFontAsync(fontName); node.fontName = fontName; node.fontSize = +fontSize;
    } catch (err) { console.warn('Error setting font size', err); }
    await setCharacters(node, text);
    node.fills = [{ type: 'SOLID', color: { r: +(fontColor.r ?? 0), g: +(fontColor.g ?? 0), b: +(fontColor.b ?? 0) }, opacity: +(fontColor.a ?? 1) } as SolidPaint];

    if (parentId) {
      const parent = await figma.getNodeByIdAsync(parentId);
      if (!parent) throw buildError(ERR.NOT_FOUND, `Parent node not found: ${parentId}`);
      if (!('appendChild' in parent)) throw buildError(ERR.WRONG_TYPE, `Parent cannot contain children: ${parentId}`);
      (parent as BaseNode & ChildrenMixin).appendChild(node);
    } else { figma.currentPage.appendChild(node); }

    return { id: node.id, name: node.name, x: node.x, y: node.y, width: node.width, height: node.height, characters: node.characters, fontSize: (node.fontSize as number), fontWeight, fontColor, fontName: node.fontName, fills: node.fills, parentId: node.parent ? node.parent.id : undefined };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleSetFillColor(params: SetFillParams) {
  try {
    if (!params?.nodeId || !params.color) throw buildError(ERR.INVALID_ARGS, 'Expected { nodeId, color }');
    const node = await figma.getNodeByIdAsync(params.nodeId);
    if (!node) throw buildError(ERR.NOT_FOUND, `Node not found with ID: ${params.nodeId}`);
    if (!('fills' in node)) throw buildError(ERR.WRONG_TYPE, `Node does not support fills: ${params.nodeId}`);
    const { r=0, g=0, b=0, a=1 } = params.color;
    (node as GeometryMixin).fills = [{ type: 'SOLID', color: { r: +r || 0, g: +g || 0, b: +b || 0 }, opacity: +a } as SolidPaint];
    return { id: node.id, name: (node as any).name, fills: (node as GeometryMixin).fills };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleSetStrokeColor(params: SetStrokeParams) {
  try {
    if (!params?.nodeId || !params.color) throw buildError(ERR.INVALID_ARGS, 'Expected { nodeId, color }');
    const node = await figma.getNodeByIdAsync(params.nodeId);
    if (!node) throw buildError(ERR.NOT_FOUND, `Node not found with ID: ${params.nodeId}`);
    if (!('strokes' in node)) throw buildError(ERR.WRONG_TYPE, `Node does not support strokes: ${params.nodeId}`);
    const { r=0, g=0, b=0, a=1 } = params.color;
    (node as GeometryMixin).strokes = [{ type: 'SOLID', color: { r, g, b }, opacity: a } as SolidPaint];
    if ('strokeWeight' in (node as any) && params.weight !== undefined) (node as any).strokeWeight = params.weight;
    return { id: node.id, name: (node as any).name, strokes: (node as GeometryMixin).strokes, strokeWeight: 'strokeWeight' in (node as any) ? (node as any).strokeWeight : undefined };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleMoveNode(params: MoveNodeParams) {
  if (!params?.nodeId || params.x === undefined || params.y === undefined) throw buildError(ERR.INVALID_ARGS, 'Expected { nodeId, x, y }');
  try {
    const node = await figma.getNodeByIdAsync(params.nodeId);
    if (!node) throw buildError(ERR.NOT_FOUND, `Node not found with ID: ${params.nodeId}`);
    if (!('x' in node) || !('y' in node)) throw buildError(ERR.WRONG_TYPE, `Node does not support position: ${params.nodeId}`);
    (node as any).x = params.x; (node as any).y = params.y;
    return { id: node.id, name: (node as any).name, x: (node as any).x, y: (node as any).y };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleResizeNode(params: ResizeNodeParams) {
  if (!params?.nodeId || params.width === undefined || params.height === undefined) throw buildError(ERR.INVALID_ARGS, 'Expected { nodeId, width, height }');
  try {
    const node = await figma.getNodeByIdAsync(params.nodeId);
    if (!node) throw buildError(ERR.NOT_FOUND, `Node not found with ID: ${params.nodeId}`);
    if (!('resize' in node)) throw buildError(ERR.WRONG_TYPE, `Node does not support resizing: ${params.nodeId}`);
    (node as LayoutMixin).resize(params.width, params.height);
    return { id: node.id, name: (node as any).name, width: (node as LayoutMixin).width, height: (node as LayoutMixin).height };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleDeleteNode(params: DeleteNodeParams) {
  if (!params?.nodeId) throw buildError(ERR.INVALID_ARGS, 'Expected { nodeId }');
  try {
    const node = await figma.getNodeByIdAsync(params.nodeId);
    if (!node) throw buildError(ERR.NOT_FOUND, `Node not found with ID: ${params.nodeId}`);
    const info = { id: node.id, name: (node as any).name, type: node.type };
    node.remove();
    return info;
  } catch (e: any) { throw normalizeError(e); }
}

async function handleGetStyles() {
  try {
    const styles = {
      colors: await figma.getLocalPaintStylesAsync(),
      texts: await figma.getLocalTextStylesAsync(),
      effects: await figma.getLocalEffectStylesAsync(),
      grids: await figma.getLocalGridStylesAsync(),
    };
    return {
      colors: styles.colors.map((s) => ({ id: s.id, name: s.name, key: s.key, paint: s.paints[0] })),
      texts: styles.texts.map((s) => ({ id: s.id, name: s.name, key: s.key, fontSize: s.fontSize, fontName: s.fontName })),
      effects: styles.effects.map((s) => ({ id: s.id, name: s.name, key: s.key })),
      grids: styles.grids.map((s) => ({ id: s.id, name: s.name, key: s.key })),
    };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleGetLocalComponents() {
  try {
    await figma.loadAllPagesAsync();
    const components = figma.root.findAllWithCriteria({ types: ['COMPONENT'] });
    return { count: components.length, components: components.map((c: any) => ({ id: c.id, name: c.name, key: 'key' in c ? c.key : null })) };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleCreateComponentInstance(params: { componentKey?: string; x?: number; y?: number }) {
  if (!params?.componentKey) throw buildError(ERR.INVALID_ARGS, 'Expected { componentKey }');
  try {
    const component = await figma.importComponentByKeyAsync(params.componentKey);
    const instance = component.createInstance();

    instance.x = params.x ?? 0;
    instance.y = params.y ?? 0;
    figma.currentPage.appendChild(instance);

    const main = instance.mainComponent; // ComponentNode | null

    return {
      id: instance.id,
      name: instance.name,
      x: instance.x,
      y: instance.y,
      width: instance.width,
      height: instance.height,

      // 🔁 Раньше вы хотели componentId — отдаём корректные поля:
      mainComponentId: main ? main.id : null,
      mainComponentKey: main && 'key' in main ? (main as any).key : null,

      // Полезно ещё вернуть сведения о том, что импортировали:
      componentNodeId: component.id,
      componentKey: 'key' in component ? (component as any).key : null,
    };
  } catch (e: any) {
    throw normalizeError(e);
  }
}

async function handleExportNodeAsImage(params: ExportImageParams) {
  if (!params?.nodeId) throw buildError(ERR.INVALID_ARGS, 'Expected { nodeId }');
  try {
    const format = params.format ?? 'PNG';
    const node = await figma.getNodeByIdAsync(params.nodeId);
    if (!node) throw buildError(ERR.NOT_FOUND, `Node not found with ID: ${params.nodeId}`);
    if (!('exportAsync' in node)) throw buildError(ERR.WRONG_TYPE, `Node does not support exporting: ${params.nodeId}`);
    const settings: ExportSettings = format === 'SVG' ? { format } as ExportSettingsSVG : { format, constraint: { type: 'SCALE', value: params.scale ?? 1 } } as ExportSettings;
    const bytes = await (node as ExportMixin).exportAsync(settings);
    const mimeType = format === 'PNG' ? 'image/png' : format === 'JPG' ? 'image/jpeg' : format === 'SVG' ? 'image/svg+xml' : format === 'PDF' ? 'application/pdf' : 'application/octet-stream';
    const base64 = customBase64Encode(bytes);
    return { nodeId: params.nodeId, format, scale: params.scale ?? (format === 'SVG' ? undefined : 1), mimeType, imageData: base64 };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleSetCornerRadius(params: CornerRadiusParams) {
  if (!params?.nodeId || params.radius === undefined) throw buildError(ERR.INVALID_ARGS, 'Expected { nodeId, radius, corners? }');
  try {
    const node = await figma.getNodeByIdAsync(params.nodeId);
    if (!node) throw buildError(ERR.NOT_FOUND, `Node not found with ID: ${params.nodeId}`);
    if (!('cornerRadius' in (node as any)) && !('topLeftRadius' in (node as any))) throw buildError(ERR.WRONG_TYPE, `Node does not support corner radius: ${params.nodeId}`);
    const n: any = node;
    const { radius, corners } = params;
    if (corners && Array.isArray(corners) && corners.length === 4 && 'topLeftRadius' in n) {
      if (corners[0]) n.topLeftRadius = radius;
      if (corners[1]) n.topRightRadius = radius;
      if (corners[2]) n.bottomRightRadius = radius;
      if (corners[3]) n.bottomLeftRadius = radius;
    } else if ('cornerRadius' in n) {
      n.cornerRadius = radius;
    }
    return { id: n.id, name: n.name, cornerRadius: 'cornerRadius' in n ? n.cornerRadius : undefined, topLeftRadius: 'topLeftRadius' in n ? n.topLeftRadius : undefined, topRightRadius: 'topRightRadius' in n ? n.topRightRadius : undefined, bottomRightRadius: 'bottomRightRadius' in n ? n.bottomRightRadius : undefined, bottomLeftRadius: 'bottomLeftRadius' in n ? n.bottomLeftRadius : undefined };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleSetTextContent(params: TextContentParams) {
  if (!params?.nodeId || params.text === undefined) throw buildError(ERR.INVALID_ARGS, 'Expected { nodeId, text }');
  try {
    const node = await figma.getNodeByIdAsync(params.nodeId);
    if (!node) throw buildError(ERR.NOT_FOUND, `Node not found with ID: ${params.nodeId}`);
    if (node.type !== 'TEXT') throw buildError(ERR.WRONG_TYPE, `Node is not TEXT: ${params.nodeId}`);
    await figma.loadFontAsync((node as TextNode).fontName as FontName);
    await setCharacters(node as TextNode, params.text);
    return { id: node.id, name: (node as any).name, characters: (node as TextNode).characters, fontName: (node as TextNode).fontName };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleCloneNode(params: CloneNodeParams) {
  if (!params?.nodeId) throw buildError(ERR.INVALID_ARGS, 'Expected { nodeId }');
  try {
    const node = await figma.getNodeByIdAsync(params.nodeId);
    if (!node) throw buildError(ERR.NOT_FOUND, `Node not found with ID: ${params.nodeId}`);
    const clone = (node as any).clone();
    if (params.x !== undefined && params.y !== undefined) {
      if (!('x' in clone) || !('y' in clone)) throw buildError(ERR.WRONG_TYPE, `Cloned node does not support position: ${params.nodeId}`);
      (clone as any).x = params.x; (clone as any).y = params.y;
    }
    if (node.parent) (node.parent as any).appendChild(clone); else figma.currentPage.appendChild(clone);
    return { id: clone.id, name: clone.name, x: 'x' in clone ? (clone as any).x : undefined, y: 'y' in clone ? (clone as any).y : undefined, width: 'width' in clone ? (clone as any).width : undefined, height: 'height' in clone ? (clone as any).height : undefined };
  } catch (e: any) { throw normalizeError(e); }
}

async function handleScanTextNodes(params: ScanTextNodesParams) {
  try {
    const { nodeId, useChunking = true, chunkSize = 10 } = params;
    const commandId = params.commandId ?? generateCommandId();
    const node = await figma.getNodeByIdAsync(nodeId);
    if (!node) {
      sendProgressUpdate(commandId, 'scan_text_nodes', 'error', 0, 0, 0, `Node with ID ${nodeId} not found`, { error: `Node not found: ${nodeId}` });
      throw buildError(ERR.NOT_FOUND, `Node with ID ${nodeId} not found`);
    }
    if (!useChunking) {
      const textNodes: any[] = [];
      sendProgressUpdate(commandId, 'scan_text_nodes', 'started', 0, 1, 0, `Starting scan of node "${(node as any).name || nodeId}" without chunking`, null);
      await findTextNodes(node as any, [], 0, textNodes);
      sendProgressUpdate(commandId, 'scan_text_nodes', 'completed', 100, textNodes.length, textNodes.length, `Scan complete. Found ${textNodes.length} text nodes.`, { textNodes });
      return { success: true, message: `Scanned ${textNodes.length} text nodes.`, count: textNodes.length, textNodes, commandId };
    }

    sendProgressUpdate(commandId, 'scan_text_nodes', 'started', 0, 0, 0, `Starting chunked scan of node "${(node as any).name || nodeId}"`, { chunkSize });
    const nodesToProcess: any[] = [];
    await collectNodesToProcess(node as any, [], 0, nodesToProcess);
    const totalNodes = nodesToProcess.length; const totalChunks = Math.ceil(totalNodes / chunkSize);
    sendProgressUpdate(commandId, 'scan_text_nodes', 'in_progress', 5, totalNodes, 0, `Found ${totalNodes} nodes to scan. Will process in ${totalChunks} chunks.`, { totalNodes, totalChunks, chunkSize });

    const allTextNodes: any[] = []; let processedNodes = 0; let chunksProcessed = 0;
    for (let i = 0; i < totalNodes; i += chunkSize) {
      const chunkEnd = Math.min(i + chunkSize, totalNodes);
      sendProgressUpdate(commandId, 'scan_text_nodes', 'in_progress', Math.round(5 + (chunksProcessed / totalChunks) * 90), totalNodes, processedNodes, `Processing chunk ${chunksProcessed + 1}/${totalChunks}`, { currentChunk: chunksProcessed + 1, totalChunks, textNodesFound: allTextNodes.length });
      const chunkNodes = nodesToProcess.slice(i, chunkEnd);
      const chunkTextNodes: any[] = [];
      for (const info of chunkNodes) {
        if (info.node.type === 'TEXT') {
          try { const textNodeInfo = await processTextNode(info.node, info.parentPath, info.depth); if (textNodeInfo) chunkTextNodes.push(textNodeInfo); } catch {}
        }
        await delay(5);
      }
      allTextNodes.push(...chunkTextNodes);
      processedNodes += chunkNodes.length; chunksProcessed++;
      sendProgressUpdate(commandId, 'scan_text_nodes', 'in_progress', Math.round(5 + (chunksProcessed / totalChunks) * 90), totalNodes, processedNodes, `Processed chunk ${chunksProcessed}/${totalChunks}. Found ${allTextNodes.length} text nodes so far.`, { currentChunk: chunksProcessed, totalChunks, processedNodes, textNodesFound: allTextNodes.length, chunkResult: chunkTextNodes });
      if (i + chunkSize < totalNodes) await delay(50);
    }

    sendProgressUpdate(commandId, 'scan_text_nodes', 'completed', 100, totalNodes, processedNodes, `Scan complete. Found ${allTextNodes.length} text nodes.`, { textNodes: allTextNodes, processedNodes, chunks: chunksProcessed });
    return { success: true, message: `Chunked scan complete. Found ${allTextNodes.length} text nodes.`, totalNodes: allTextNodes.length, processedNodes, chunks: chunksProcessed, textNodes: allTextNodes, commandId };
  } catch (e: any) { throw normalizeError(e); }
}

// helpers for scanning
async function collectNodesToProcess(node: any, parentPath: string[] = [], depth = 0, nodesToProcess: any[] = []) {
  if ((node as any).visible === false) return;
  const nodePath = [...parentPath, (node as any).name || `Unnamed ${node.type}`];
  nodesToProcess.push({ node, parentPath: nodePath, depth });
  if ('children' in node) for (const child of (node as any).children) await collectNodesToProcess(child, nodePath, depth + 1, nodesToProcess);
}

async function processTextNode(node: any, parentPath: string[], depth: number) {
  if (node.type !== 'TEXT') return null;
  try {
    let fontFamily = '', fontStyle = '';
    if (node.fontName && typeof node.fontName === 'object') {
      if ('family' in node.fontName) fontFamily = node.fontName.family;
      if ('style' in node.fontName) fontStyle = node.fontName.style;
    }
    const safe = { id: node.id, name: node.name || 'Text', type: node.type, characters: node.characters, fontSize: typeof node.fontSize === 'number' ? node.fontSize : 0, fontFamily, fontStyle, x: typeof node.x === 'number' ? node.x : 0, y: typeof node.y === 'number' ? node.y : 0, width: typeof node.width === 'number' ? node.width : 0, height: typeof node.height === 'number' ? node.height : 0, path: parentPath.join(' > '), depth };
    try {
      const originalFills = JSON.parse(JSON.stringify(node.fills));
      node.fills = [{ type: 'SOLID', color: { r: 1, g: 0.5, b: 0 }, opacity: 0.3 }];
      await delay(100);
      try { node.fills = originalFills; } catch {}
    } catch {}
    return safe;
  } catch (err) { return null; }
}

async function findTextNodes(node: any, parentPath: string[] = [], depth = 0, textNodes: any[] = []) {
  if ((node as any).visible === false) return;
  const nodePath = [...parentPath, (node as any).name || `Unnamed ${node.type}`];
  if (node.type === 'TEXT') {
    try {
      let fontFamily = '', fontStyle = '';
      if (node.fontName && typeof node.fontName === 'object') {
        if ('family' in node.fontName) fontFamily = node.fontName.family;
        if ('style' in node.fontName) fontStyle = node.fontName.style;
      }
      const safe = { id: node.id, name: node.name || 'Text', type: node.type, characters: node.characters, fontSize: typeof node.fontSize === 'number' ? node.fontSize : 0, fontFamily, fontStyle, x: typeof node.x === 'number' ? node.x : 0, y: typeof node.y === 'number' ? node.y : 0, width: typeof node.width === 'number' ? node.width : 0, height: typeof node.height === 'number' ? node.height : 0, path: nodePath.join(' > '), depth };
      try {
        const originalFills = JSON.parse(JSON.stringify(node.fills));
        node.fills = [{ type: 'SOLID', color: { r: 1, g: 0.5, b: 0 }, opacity: 0.3 }];
        await delay(500);
        try { node.fills = originalFills; } catch {}
      } catch {}
      textNodes.push(safe);
    } catch {}
  }
  if ('children' in node) for (const child of (node as any).children) await findTextNodes(child, nodePath, depth + 1, textNodes);
}

async function handleSetMultipleTextContents(params: BatchTextReplacementParams) {
  try {
    const { nodeId, text } = params;
    const commandId = params.commandId ?? generateCommandId();
    if (!nodeId || !text || !Array.isArray(text)) {
      const msg = 'Missing required parameters: nodeId and text array';
      sendProgressUpdate(commandId, 'set_multiple_text_contents', 'error', 0, 0, 0, msg, { error: msg });
      throw buildError(ERR.INVALID_ARGS, msg);
    }

    sendProgressUpdate(commandId, 'set_multiple_text_contents', 'started', 0, text.length, 0, `Starting text replacement for ${text.length} nodes`, { totalReplacements: text.length });

    const results: any[] = []; let successCount = 0; let failureCount = 0;
    const CHUNK_SIZE = 5; const chunks: typeof text[] = [];
    for (let i = 0; i < text.length; i += CHUNK_SIZE) chunks.push(text.slice(i, i + CHUNK_SIZE));
    sendProgressUpdate(commandId, 'set_multiple_text_contents', 'in_progress', 5, text.length, 0, `Preparing to replace text in ${text.length} nodes using ${chunks.length} chunks`, { totalReplacements: text.length, chunks: chunks.length, chunkSize: CHUNK_SIZE });

    for (let chunkIndex = 0; chunkIndex < chunks.length; chunkIndex++) {
      const chunk = chunks[chunkIndex];
      sendProgressUpdate(commandId, 'set_multiple_text_contents', 'in_progress', Math.round(5 + (chunkIndex / chunks.length) * 90), text.length, successCount + failureCount, `Processing text replacements chunk ${chunkIndex + 1}/${chunks.length}`, { currentChunk: chunkIndex + 1, totalChunks: chunks.length, successCount, failureCount });

      const chunkResults = await Promise.all(chunk.map(async (rep) => {
        if (!rep.nodeId || rep.text === undefined) {
          return { success: false, nodeId: rep.nodeId || 'unknown', error: 'Missing nodeId or text in replacement entry' };
        }
        try {
          const textNode = await figma.getNodeByIdAsync(rep.nodeId);
          if (!textNode) return { success: false, nodeId: rep.nodeId, error: `Node not found: ${rep.nodeId}` };
          if (textNode.type !== 'TEXT') return { success: false, nodeId: rep.nodeId, error: `Node is not TEXT: ${rep.nodeId} (type: ${textNode.type})` };
          const originalText = (textNode as TextNode).characters;
          let originalFills: Paint[] | undefined;
          try { originalFills = JSON.parse(JSON.stringify((textNode as TextNode).fills)); (textNode as TextNode).fills = [{ type: 'SOLID', color: { r: 1, g: 0.5, b: 0 }, opacity: 0.3 }]; } catch {}
          await handleSetTextContent({ nodeId: rep.nodeId, text: rep.text });
          if (originalFills) { try { await delay(500); (textNode as TextNode).fills = originalFills; } catch {} }
          return { success: true, nodeId: rep.nodeId, originalText, translatedText: rep.text };
        } catch (err: any) {
          return { success: false, nodeId: rep.nodeId, error: `Error applying replacement: ${err.message}` };
        }
      }));

      chunkResults.forEach((r) => { if (r.success) successCount++; else failureCount++; results.push(r); });
      sendProgressUpdate(commandId, 'set_multiple_text_contents', 'in_progress', Math.round(5 + ((chunkIndex + 1) / chunks.length) * 90), text.length, successCount + failureCount, `Completed chunk ${chunkIndex + 1}/${chunks.length}. ${successCount} successful, ${failureCount} failed so far.`, { currentChunk: chunkIndex + 1, totalChunks: chunks.length, successCount, failureCount, chunkResults });
      if (chunkIndex < chunks.length - 1) await delay(1000);
    }

    sendProgressUpdate(commandId, 'set_multiple_text_contents', 'completed', 100, text.length, successCount + failureCount, `Text replacement complete: ${successCount} successful, ${failureCount} failed`, { totalReplacements: text.length, replacementsApplied: successCount, replacementsFailed: failureCount, completedInChunks: chunks.length, results });
    return { success: successCount > 0, nodeId, replacementsApplied: successCount, replacementsFailed: failureCount, totalReplacements: text.length, results, completedInChunks: chunks.length, commandId };
  } catch (e: any) { throw normalizeError(e); }
}

async function loadAllFontsForTextNode(node: TextNode) {
  const len = node.characters.length;
  if (len === 0) return;
  try {
    const fonts = node.getRangeAllFontNames(0, len);
    const unique: { family: string; style: string }[] = [];
    const seen = new Set<string>();
    for (const f of fonts) {
      const key = `${f.family}__${f.style}`;
      if (!seen.has(key)) {
        seen.add(key);
        unique.push(f);
      }
    }
    for (const f of unique) {
      await figma.loadFontAsync(f);
    }
  } catch (err) {
    // If font name is mixed or invalid, try loading the node's fontName if it's a single value
    const fn = node.fontName as FontName | PluginAPI['mixed'];
    if (fn !== figma.mixed) {
      await figma.loadFontAsync(fn as FontName);
    } else {
      throw err;
    }
  }
}

async function handleReplaceText(args: { nodeId?: string; text?: string }) {
  if (!args || typeof args.nodeId !== 'string' || typeof args.text !== 'string') {
    throw buildError(2000, 'Invalid arguments. Expected { nodeId: string, text: string }');
  }

  // ⬇️ ВАЖНО: асинхронный доступ к ноде
  const node = await figma.getNodeByIdAsync(args.nodeId);

  if (!node) {
    throw buildError(2001, `Node not found: ${args.nodeId}`);
  }
  if (node.type !== 'TEXT') {
    throw buildError(2002, `Node is not a TEXT node: ${node.type}`);
  }

  const textNode = node as TextNode;
  try {
    await loadAllFontsForTextNode(textNode);
    textNode.characters = args.text;
    return { nodeId: node.id, length: args.text.length };
  } catch (e: any) {
    throw buildError(2003, 'Failed to replace text (font load or edit error)', { message: String(e?.message ?? e) });
  }
}

// ---- Типы и утилиты ----
type Json = Record<string, any>;
type Handler<A = any, R = any> = (args: A) => Promise<R> | R;

// ---- Роутер команд ----
function defineCommands<T extends Record<string, Handler>>(defs: T) {
  return defs;
}

// ---- Регистр команд (без switch) ----
const commands = defineCommands({
  get_document_info: handleGetDocumentInfo,
  get_selection: handleGetSelection,
  get_node_info: handleGetNodeInfo,
  get_nodes_info: handleGetNodesInfo,
  get_reactions: handleGetReactions,
  read_my_design: handleReadMyDesign,
  create_rectangle: handleCreateRectangle,
  create_frame: handleCreateFrame,
  create_text: handleCreateText,
  set_fill_color: handleSetFillColor,
  set_stroke_color: handleSetStrokeColor,
  move_node: handleMoveNode,
  resize_node: handleResizeNode,
  delete_node: handleDeleteNode,
  get_styles: handleGetStyles,
  get_local_components: handleGetLocalComponents,
  create_component_instance: handleCreateComponentInstance,
  export_node_as_image: handleExportNodeAsImage,
  set_corner_radius: handleSetCornerRadius,
  set_text_content: handleSetTextContent,
  clone_node: handleCloneNode,
  scan_text_nodes: handleScanTextNodes,
  set_multiple_text_contents: handleSetMultipleTextContents,
  replace_text: handleReplaceText,

} as const);

type CommandName = keyof typeof commands;

// (опц.) отдельная таблица валидаторов по имени команды:
const validators: Partial<Record<CommandName, (a: Json) => Json>> = {
  replace_text: (a) => {
    if (!a || typeof a.nodeId !== 'string' || typeof a.text !== 'string') {
      throw buildError(2000, 'Invalid arguments. Expected { nodeId: string, text: string }');
    }
    return a;
  },
  // create_text: (a) => { ...; return a; },
};

// Receive messages from UI (proxied from server)
figma.ui.onmessage = async (msg: any) => {
  if (!msg || typeof msg !== 'object' || msg.type !== 'command') return;

  const requestId: string = msg.requestId;
  const name: string = msg.name;
  const args: any = msg.args ?? {};
  const respond = (payload: any) => figma.ui.postMessage(payload);

  const handler = (commands as Record<string, Handler>)[name];
  if (!handler) {
    respond({ type: 'response', requestId, ok: false, error: buildError(2004, `Unknown command: ${name}`) });
    return;
  }

  try {
    // Валидация, если есть
    const validate = (validators as Record<string, (a: Json) => Json>)[name];
    const safeArgs = validate ? validate(args) : args;

    const result = await handler(safeArgs);
    respond({ type: 'response', requestId, ok: true, result });
  } catch (err) {
    respond({ type: 'response', requestId, ok: false, error: normalizeError(err) });
  }
};
