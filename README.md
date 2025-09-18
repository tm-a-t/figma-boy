# Figma Boy: Figma integration with IntelliJ & Junie

- Open Figma in an IntelliJ panel to explore and edit the design right away.
- Ask Junie to edit your prototypes.
- Ask Junie to implement the prototypes in your codebase.

> [!NOTE]
> The setup takes more steps until we publish the plugins. Technically, Figma Boy is an IntelliJ plugin and a Figma plugin that work together. When we publish both plugins to the marketplaces, the setup will be as easy as finding them online.

## How to run

1. Go to `figma-plugin` directory and run the following commands

```shell
npm install
npx tsc -p . 
```

2. Run Figma Desktop app
3. Select `Plugins > Development > Open > Import plugin from manifest...` and choose `figma-mcp` directory
4. Open any Figma project and run the Figma Boy plugin

5. Run an IDEA instance with the plugin with

```shell
./gradlew runIde
```

6. Add Figma Boy MCP server in the Settings -> Tools -> Junie -> MCP Settings by pasting the config below

```json
{
  "mcpServers": {
    "figma-boy": {
      "command": "npx",
      "args": [
        "mcp-remote",
        "http://127.0.0.1:4114/sse"
      ]
    }
  }
}
```

7. The integration should work now! You can open the Figma panel (the Google authentication won't work for now) and
   start using Junie to build your UI

## Problem

Developing UI is a cycle: design, code, iterate. Even if you vibecode, you may need design prototypes to edit them
visually and have a source of truth for AI.

In 2025, it probably looks like this: open Figma, use AI tools in Figma to design, get the pictures, show them to AI in
the IDE, ask to implement, and retry until the result looks good enough.

We will simplify this process and put everything in one place (IntelliJ, of course).


- idea-plugin
    - Plugin, implemented as a view of a browser, allowing to log into Figma account and interact with your projects
      directly from Intellij
- Figma MCP Server
    - TODO
