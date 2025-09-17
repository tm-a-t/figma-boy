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

// Utility: build error object compatible with server's JsonRpcError model
function buildError(code: number, message: string, data?: any) {
  return { code, message, data };
}

// Handler implementations for commands coming from the server
async function handleGetSelection() {
  const nodes = figma.currentPage.selection.map((n) => ({
    id: n.id,
    type: n.type,
    name: (n as any).name ?? null,
  }));
  return { nodes };
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
  const node = figma.getNodeById(args.nodeId);
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

// Receive messages from UI (proxied from server)
figma.ui.onmessage = async (msg: any) => {
  if (!msg || typeof msg !== 'object') return;

  // Only handle command messages
  if (msg.type !== 'command') return;

  const requestId: string = msg.requestId;
  const name: string = msg.name;
  const args: any = msg.args ?? {};

  const respond = (payload: any) => figma.ui.postMessage(payload);

  try {
    let result: any;
    switch (name) {
      case 'get_selection':
        result = await handleGetSelection();
        break;
      case 'replace_text':
        result = await handleReplaceText(args);
        break;
      default:
        respond({
          type: 'response',
          requestId,
          ok: false,
          error: buildError(2004, `Unknown command: ${name}`),
        });
        return;
    }

    respond({ type: 'response', requestId, ok: true, result });
  } catch (err: any) {
    // If err is our buildError shape, pass it through; else wrap
    const e = err && typeof err === 'object' && 'code' in err ? err : buildError(2999, String(err?.message ?? err));
    figma.ui.postMessage({ type: 'response', requestId, ok: false, error: e });
  }
};
