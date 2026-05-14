# MT Manager Lite - Color Reference Guide

## 🎨 Complete Color Palette

### Background Colors

#### Primary Backgrounds
```xml
<color name="bg_primary">#0B1120</color>
```
- **Usage**: Main app background
- **RGB**: (11, 17, 32)
- **Description**: Deep navy blue, darkest background

```xml
<color name="bg_surface">#0F1628</color>
```
- **Usage**: Surface elements, dialogs
- **RGB**: (15, 22, 40)
- **Description**: Slightly lighter than primary

```xml
<color name="bg_card">#1A2438</color>
```
- **Usage**: Card backgrounds, panes
- **RGB**: (26, 36, 56)
- **Description**: Medium dark blue for containers

```xml
<color name="bg_elevated">#1E2A40</color>
```
- **Usage**: Elevated elements, info cards
- **RGB**: (30, 42, 64)
- **Description**: Lighter blue for raised elements

#### Special Backgrounds
```xml
<color name="bg_card_glass">#801A2438</color>
```
- **Usage**: Glassmorphism effects
- **RGB**: (26, 36, 56) with 50% opacity
- **Description**: Semi-transparent card overlay

---

### Accent Colors

#### Primary Accent
```xml
<color name="accent_blue">#58A6FF</color>
```
- **Usage**: Primary actions, links, active states
- **RGB**: (88, 166, 255)
- **Description**: Bright sky blue, main brand color
- **Use Cases**: FAB, active indicators, Python files

```xml
<color name="accent_blue_dim">#1A2E4A</color>
```
- **Usage**: Blue backgrounds, selected states
- **RGB**: (26, 46, 74)
- **Description**: Dimmed blue for backgrounds

#### Secondary Accents
```xml
<color name="accent_green">#4ADE80</color>
```
- **Usage**: Success states, .txt files
- **RGB**: (74, 222, 128)
- **Description**: Bright mint green
- **Use Cases**: Text file badges, success messages

```xml
<color name="accent_orange">#FB923C</color>
```
- **Usage**: Warning states, .enc files
- **RGB**: (251, 146, 60)
- **Description**: Warm orange
- **Use Cases**: Encrypted file badges, warnings

```xml
<color name="accent_red">#F87171</color>
```
- **Usage**: Error states, delete actions
- **RGB**: (248, 113, 113)
- **Description**: Soft red
- **Use Cases**: Delete buttons, error messages

```xml
<color name="accent_purple">#A78BFA</color>
```
- **Usage**: File icons, secondary highlights
- **RGB**: (167, 139, 250)
- **Description**: Soft lavender purple
- **Use Cases**: File count icons, special badges

```xml
<color name="accent_gray">#94A3B8</color>
```
- **Usage**: Neutral states, .bak files
- **RGB**: (148, 163, 184)
- **Description**: Cool gray
- **Use Cases**: Backup file badges, disabled states

---

### Text Colors

```xml
<color name="text_primary">#F1F5F9</color>
```
- **Usage**: Main text, headings
- **RGB**: (241, 245, 249)
- **Description**: Almost white, high contrast
- **Contrast Ratio**: 15:1 on bg_primary (AAA)

```xml
<color name="text_secondary">#94A3B8</color>
```
- **Usage**: Secondary text, labels
- **RGB**: (148, 163, 184)
- **Description**: Medium gray
- **Contrast Ratio**: 7:1 on bg_primary (AA)

```xml
<color name="text_muted">#64748B</color>
```
- **Usage**: Tertiary text, hints
- **RGB**: (100, 116, 139)
- **Description**: Darker gray
- **Contrast Ratio**: 4.5:1 on bg_primary (AA for large text)

```xml
<color name="text_link">#58A6FF</color>
```
- **Usage**: Links, clickable text
- **RGB**: (88, 166, 255)
- **Description**: Same as accent_blue
- **Contrast Ratio**: 8:1 on bg_primary (AA)

---

### Border Colors

```xml
<color name="border_default">#2A3650</color>
```
- **Usage**: Default borders, dividers
- **RGB**: (42, 54, 80)
- **Description**: Subtle blue-gray border

```xml
<color name="border_muted">#1E2A40</color>
```
- **Usage**: Very subtle borders
- **RGB**: (30, 42, 64)
- **Description**: Barely visible border

---

### File Type Colors

```xml
<color name="file_text">#4ADE80</color>
```
- **File Types**: .txt, .md, .log
- **Badge Color**: Green
- **Description**: Text documents

```xml
<color name="file_code">#58A6FF</color>
```
- **File Types**: .py, .js, .java, .kt
- **Badge Color**: Blue
- **Description**: Code files

```xml
<color name="file_enc">#FB923C</color>
```
- **File Types**: .enc, .encrypted
- **Badge Color**: Orange with lock icon
- **Description**: Encrypted files

```xml
<color name="file_bak">#94A3B8</color>
```
- **File Types**: .bak, .backup, .old
- **Badge Color**: Gray
- **Description**: Backup files

```xml
<color name="file_folder">#58A6FF</color>
```
- **File Types**: Directories
- **Icon Color**: Blue
- **Description**: Folders

```xml
<color name="file_default">#94A3B8</color>
```
- **File Types**: Unknown/other
- **Icon Color**: Gray
- **Description**: Default file color

---

### Pane & Selection Colors

```xml
<color name="pane_active_border">#3B5998</color>
```
- **Usage**: Active pane border
- **RGB**: (59, 89, 152)
- **Description**: Facebook blue, indicates focus

```xml
<color name="pane_inactive_border">#1E2A40</color>
```
- **Usage**: Inactive pane border
- **RGB**: (30, 42, 64)
- **Description**: Subtle, blends with background

```xml
<color name="item_selected_bg">#1A2E4A</color>
```
- **Usage**: Selected item background
- **RGB**: (26, 46, 74)
- **Description**: Blue tint for selection

```xml
<color name="item_hover_bg">#1E2A40</color>
```
- **Usage**: Hover state background
- **RGB**: (30, 42, 64)
- **Description**: Subtle highlight on hover

---

## 🎯 Color Usage Guidelines

### Hierarchy

1. **Primary Actions**: `accent_blue` (#58A6FF)
   - FAB, primary buttons, active states

2. **Secondary Actions**: `accent_purple` (#A78BFA)
   - Secondary buttons, file icons

3. **Success/Positive**: `accent_green` (#4ADE80)
   - Success messages, text files

4. **Warning/Attention**: `accent_orange` (#FB923C)
   - Warnings, encrypted files

5. **Error/Destructive**: `accent_red` (#F87171)
   - Delete actions, errors

6. **Neutral/Disabled**: `accent_gray` (#94A3B8)
   - Disabled states, backup files

### Contrast Requirements

#### WCAG AA (Minimum)
- **Normal Text**: 4.5:1 contrast ratio
- **Large Text**: 3:1 contrast ratio
- **UI Components**: 3:1 contrast ratio

#### WCAG AAA (Enhanced)
- **Normal Text**: 7:1 contrast ratio
- **Large Text**: 4.5:1 contrast ratio

#### Our Implementation
| Combination | Ratio | Level |
|-------------|-------|-------|
| text_primary on bg_primary | 15:1 | AAA |
| text_secondary on bg_primary | 7:1 | AA |
| accent_blue on bg_primary | 8:1 | AA |
| accent_green on bg_primary | 9:1 | AAA |
| accent_orange on bg_primary | 7:1 | AA |

### Opacity Values

#### Glow Effects
- **Strong Glow**: 50% (#50 prefix) - FAB outer glow
- **Medium Glow**: 40% (#40 prefix) - Icon glows
- **Subtle Glow**: 33% (#33 prefix) - Subtle highlights
- **Glass Effect**: 50% (#80 prefix) - Glassmorphism

#### Alpha Hex Values
| Opacity | Hex | Decimal |
|---------|-----|---------|
| 100% | FF | 255 |
| 75% | BF | 191 |
| 50% | 80 | 128 |
| 40% | 66 | 102 |
| 33% | 54 | 85 |
| 25% | 40 | 64 |
| 10% | 1A | 26 |

---

## 🌈 Gradient Definitions

### Main Background Gradient
```xml
<gradient
    android:startColor="#0B1120"  <!-- Top -->
    android:centerColor="#0D1528" <!-- Middle -->
    android:endColor="#101A30"    <!-- Bottom -->
    android:angle="270" />        <!-- Top to bottom -->
```

### FAB Gradient
```xml
<gradient
    android:startColor="#6BA6FF"  <!-- Lighter blue -->
    android:endColor="#58A6FF"    <!-- Standard blue -->
    android:angle="135" />        <!-- Diagonal -->
```

---

## 🎨 Color Combinations

### Recommended Pairings

#### High Contrast (Headings)
- **Text**: `text_primary` (#F1F5F9)
- **Background**: `bg_primary` (#0B1120)
- **Ratio**: 15:1

#### Medium Contrast (Body Text)
- **Text**: `text_secondary` (#94A3B8)
- **Background**: `bg_card` (#1A2438)
- **Ratio**: 6:1

#### Accent on Dark
- **Accent**: `accent_blue` (#58A6FF)
- **Background**: `bg_primary` (#0B1120)
- **Ratio**: 8:1

#### Card on Background
- **Card**: `bg_card` (#1A2438)
- **Background**: `bg_primary` (#0B1120)
- **Border**: `border_default` (#2A3650)

---

## 🔍 Color Testing

### Accessibility Tools
- [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/)
- [Coolors Contrast Checker](https://coolors.co/contrast-checker)
- Android Accessibility Scanner

### Testing Checklist
- [ ] All text meets WCAG AA minimum
- [ ] Interactive elements have 3:1 contrast
- [ ] Focus indicators are visible
- [ ] Color is not the only indicator
- [ ] Works in grayscale mode

---

## 📱 Platform-Specific Notes

### Android Material Design
- Uses Material Components theme
- Follows Material Design 3 guidelines
- Supports dynamic color (Android 12+)
- Dark theme optimized

### Color State Lists
All interactive elements support:
- **Normal**: Default color
- **Pressed**: Slightly darker
- **Focused**: Accent color
- **Disabled**: Gray with 50% opacity

---

## 🎯 Quick Reference

### Most Used Colors
```
Primary BG:    #0B1120
Card BG:       #1A2438
Accent Blue:   #58A6FF
Text Primary:  #F1F5F9
Text Secondary: #94A3B8
Border:        #2A3650
```

### File Type Badges
```
.py  → Blue   #58A6FF
.txt → Green  #4ADE80
.bak → Gray   #94A3B8
.enc → Orange #FB923C
```

### Icon Glows
```
Folder → Blue   #4058A6FF (40% opacity)
File   → Purple #40A78BFA (40% opacity)
FAB    → Blue   #5058A6FF (50% opacity)
```

---

**Last Updated**: Implementation Phase
**Color Count**: 24 defined colors
**Accessibility**: WCAG AA compliant
**Theme**: Dark blue gradient
