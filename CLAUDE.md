# Bylins Client - Claude Code Instructions

## Architecture

Bylins Client - кроссплатформенный MUD-клиент на базе Compose Multiplatform.

## Cross-Platform Requirements

### Dialogs

All dialogs MUST follow these rules:

1. **Use `Dialog` not `DialogWindow`** - `DialogWindow` is desktop-only
2. **Scrollable content** - Wrap content in `Column(Modifier.weight(1f, fill = false).verticalScroll(scrollState))`
3. **Sticky buttons** - Cancel/Save buttons MUST be outside the scroll area, at the bottom
4. **Structure**:
   ```kotlin
   Dialog(onDismissRequest = onDismiss) {
       Surface(...) {
           Column(Modifier.padding(16.dp)) {
               // Header (fixed)
               Text("Title", ...)

               // Scrollable content
               Column(Modifier.weight(1f, fill = false).verticalScroll(scrollState)) {
                   // Form fields here
               }

               // Divider
               Divider()

               // Sticky buttons (fixed)
               Row(...) {
                   TextButton(onClick = onDismiss) { Text("Cancel") }
                   Button(onClick = onSave) { Text("Save") }
               }
           }
       }
   }
   ```

### File Selection

- **DO NOT use `JFileChooser`** - it's Swing/Desktop-only
- Use `FilePickerDialog` from `com.bylins.client.ui.components`
- Example:
  ```kotlin
  var showDialog by remember { mutableStateOf(false) }

  Button(onClick = { showDialog = true }) { Text("Select File") }

  if (showDialog) {
      FilePickerDialog(
          mode = FilePickerMode.OPEN,  // or SAVE
          title = "Select file",
          initialDirectory = File(System.getProperty("user.home")),
          extensions = listOf("json", "yaml"),
          onDismiss = { showDialog = false },
          onFileSelected = { file ->
              // Handle file
              showDialog = false
          }
      )
  }
  ```

### Opening Directories

- **DO NOT use `Desktop.getDesktop().open()`** directly - add fallback
- Use cross-platform pattern:
  ```kotlin
  private fun openDirectory(path: String) {
      try {
          val os = System.getProperty("os.name").lowercase()
          when {
              os.contains("win") -> Runtime.getRuntime().exec(arrayOf("explorer", path))
              os.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", path))
              else -> Runtime.getRuntime().exec(arrayOf("xdg-open", path))
          }
      } catch (e: Exception) {
          logger.error { "Error opening directory: ${e.message}" }
      }
  }
  ```

### Cursor Icons

- `java.awt.Cursor` with `PointerIcon` is acceptable for Desktop-only resize cursors
- For general cursors, prefer Compose's `PointerIcon.Default`, `PointerIcon.Hand`, `PointerIcon.Text`

## Material Components

- Project uses **Material 2** (`androidx.compose.material`)
- Use `Divider` not `HorizontalDivider` (HorizontalDivider is Material 3)
- Color scheme: use `LocalAppColorScheme.current`

## JSON Serialization

- Use `kotlinx.serialization.json` not Jackson
- Example:
  ```kotlin
  import kotlinx.serialization.json.*

  val json = Json { prettyPrint = true }
  val jsonString = json.encodeToString(data)
  val parsed = json.decodeFromString<Type>(jsonString)
  ```

## Profiles System

- Aliases, Triggers, Hotkeys can belong to "Base" (null source) or a Profile
- Use `getAllXXXWithSource()` to get items with their source
- Items from profiles are combined with base items in active stack
