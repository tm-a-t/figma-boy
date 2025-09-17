## Problem 

Developing UI is a cycle: design, code, iterate. Even if you vibecode, you may need design prototypes to edit them visually and have a source of truth for AI.

In 2025, it probably looks like this: open Figma, use AI tools in Figma to design, get the pictures, show them to AI in the IDE, ask to implement, and retry until the result looks good enough.

We will simplify this process and put everything in one place (IntelliJ, of course).

## Features 

1) Junie edits Figma prototypes
  - Tool calling using the recently released Figma MCP
2) User views and tweaks design from IDE
  - A Figma pane for IntelliJ with the UI tailored to our use case
3) Junie codes and compares UI with Figma prototypes
  - Providing Junie with design parameters and pictures, tool calling to verify the results

## Project structure
 - idea-plugin
    -  Plugin, implemented as a view of a browser, allowing to log into Figma account and interact with your projects directly from Intellij
 - Figma MCP Server
    - TODO

## How to run
- Idea Plugin

    From root:
    ```bash
    ./gradlew :idea-plugin:runIde 
    ```
    or alternatively by alias:
    ```bash
    ./gradlew runIde 
    ```
- FIgma MCP server
  
    TODO
