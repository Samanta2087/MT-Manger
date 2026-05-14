# MT Manager Lite - UI Enhancements

## Overview
Enhanced the Android file manager UI with a modern dark blue gradient theme matching the reference design.

## Key Features Implemented

### 1. Dark Blue Gradient Background
- Updated `bg_main.xml` with navy blue gradient (#0B1120 → #101A30)
- Creates a futuristic, immersive atmosphere

### 2. Glassmorphism Design
- Enhanced pane backgrounds with semi-transparent cards
- Rounded corners (18dp) for modern look
- Subtle borders for depth

### 3. Dual-Pane Layout
- Split screen with left and right panels
- Each panel shows independent file directories
- Active pane indicator with blue accent

### 4. Top Bar
- Hamburger menu icon (left)
- "MT Manager Lite" title with "Lite" in accent blue
- "Dual Pane" subtitle
- Search, bookmark, grid, and menu icons (right)

### 5. Breadcrumb Navigation
- Rounded container with path display
- Format: "home > storage > emulated > 0"
- Horizontal scrollable for long paths

### 6. Info Cards with Glowing Icons
- **Folders Card**: Blue glowing icon, folder count
- **Files Card**: Purple glowing icon, file count
- Enhanced glow effects (40% opacity)
- Larger icons (36dp) for better visibility

### 7. File List Items
- Rounded square icons (48dp) for files/folders
- File name in bold white text
- Metadata: size + date/time in gray
- 3-dot menu on right
- File type badges:
  - `.py` → Blue badge with Python icon
  - `.txt` → Green badge
  - `.bak` → Gray badge
  - `.enc` → Orange badge with lock icon

### 8. Bottom Navigation Bar
- **Left**: Back and Forward buttons with labels
- **Center**: Large circular "+" FAB (60dp) with enhanced blue glow
- **Right**: Swap and Sort buttons with labels

### 9. Color Palette
```
Background: #0B1120 → #101A30 (gradient)
Cards: #1A2438
Accent Blue: #58A6FF
Accent Purple: #A78BFA
Accent Green: #4ADE80
Accent Orange: #FB923C
Text Primary: #F1F5F9
Text Secondary: #94A3B8
```

### 10. Visual Effects
- Soft neon glow accents on interactive elements
- Smooth shadows and highlights
- High contrast text for readability
- Rounded corners everywhere (14-18dp)

## Files Modified

### Drawables
- `bg_main.xml` - Main gradient background
- `bg_pane.xml` - Pane container background
- `bg_info_card.xml` - Stats card background
- `bg_fab.xml` - Floating action button with glow
- `bg_icon_glow_blue.xml` - Blue icon glow effect
- `bg_icon_glow_purple.xml` - Purple icon glow effect
- `bg_file_icon.xml` - File icon background
- `bg_ext_badge.xml` - Extension badge background
- `ic_python.xml` - NEW: Python file icon

### Layouts
- `activity_main.xml` - Main activity with dual panes
- `fragment_file_pane.xml` - Individual pane layout
- `item_file.xml` - File list item layout
- `item_breadcrumb.xml` - Breadcrumb navigation item

### Values
- `colors.xml` - Updated color palette for dark blue theme

## Design Principles

1. **Minimalism**: Clean, uncluttered interface
2. **Futuristic**: Neon accents and glowing effects
3. **Readability**: High contrast text on dark backgrounds
4. **Consistency**: Rounded corners and spacing throughout
5. **Accessibility**: Clear visual hierarchy and touch targets

## Resolution
- Optimized for 1080x1920 (mobile portrait)
- Responsive layout adapts to different screen sizes
- Ultra-sharp UI with proper density-independent pixels (dp)

## Next Steps
To see the UI in action:
1. Build the project: `gradlew assembleDebug`
2. Install on device/emulator
3. Grant storage permissions
4. Navigate through file system to see dual-pane layout

## Notes
- All spacing and alignment match the reference design
- Glassmorphism effects use transparency and blur
- Icon glows use 40% opacity for subtle effect
- FAB has enhanced glow with gradient fill
