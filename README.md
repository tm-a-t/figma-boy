<div align="center">

<img src="https://en.meming.world/images/en/thumb/1/18/Giga_Chad.jpg/300px-Giga_Chad.jpg" alt="gigachad">

# ✨ Figma Boy ✨ <br><sub>Figma support for IntelliJ & Junie</sub>

</div>
<br>

- Explore and edit your design files in an IntelliJ panel
- Ask Junie to edit your prototypes
- Ask Junie to implement the prototypes in your codebase

https://github.com/user-attachments/assets/4f650d7f-1ae9-45fe-944f-f9a81859218b

Figma Boy is an alternative to Figma’s official [Dev Mode MCP](https://help.figma.com/hc/en-us/articles/32132100833559-Guide-to-the-Dev-Mode-MCP-Server) that lets agents generate code from designs. We aim for more capabilities and tighter integration with Junie & IntelliJ.

## How to use

✨ **For now, you need to build Figma Boy locally (see the instructions below).** The project consists of an IntelliJ plugin and a Figma plugin that work together. When we publish both plugins, the setup will be as easy as saving them on the marketplaces.

✨ **This is a hackathon project—please star it and show support if you want us to continue our work.** We are open to actively working on Figma Boy if there is a request from the community.

## Running dev version

1. Run the Figma plugin
    1. Go to `figma-plugin` directory and run the following commands:
        ```shell
        npm install
        npx tsc -p . 
        ```
    2. Run Figma Desktop app
    3. Select `Plugins > Development > Open > Import plugin from manifest...` and choose `figma-mcp/manifest.json`
    4. Run the Figma Boy plugin

2. Start an IntelliJ instance with the IntelliJ plugin:

    ```shell
    ./gradlew runIde
    ```

3. Save Figma Boy MCP server: in Settings -> Tools -> Junie -> MCP Settings, paste the config below

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

4. The integration should work now! You can open the Figma panel (the Google authentication won't work for now) and ask Junie to build your UI


