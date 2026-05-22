# 🖍 Whiteboard App — Tata ClassEdge Assignment

An Android whiteboard application built in Kotlin for Interactive Flat Panels (IFPs).

##  Features

- ✏ Freehand drawing with smooth bezier curves
-  Real pixel-based eraser (PorterDuff.Mode.CLEAR)
-  Color palette with 8 colors
-  Adjustable stroke width via slider
-  Shapes: Rectangle, Circle, Line, Polygon
-  Text: Insert, Edit, Move, Delete
-  Save/Load whiteboard as JSON (local storage)
-  Export canvas as PNG image
-  Undo / Redo (50 step history)
- 🗑 Clear canvas with confirmation

##  Architecture

MVVM + Clean Architecture

    ├── model/          → StrokeModel, ShapeModel, TextModel, WhiteboardData
    ├── views/          → WhiteboardView (custom Canvas)
    ├── ui/
    │   ├── activity/   → MainActivity
    │   ├── adapter/    → ToolAdapter, ColorAdapter
    │   └── viewmodels/ → WhiteboardViewModel
    ├── domain/
    │   ├── usecase/    → SaveWhiteboardUseCase, LoadWhiteboardUseCase
    │   └── repository/ → WhiteboardRepository

##  File Format

Whiteboards are saved as JSON in internal storage:
`/Android/data/com.example.tataclassedgeassignment/files/`

Filename format: `whiteboard_yyyyMMdd_HHmmss.json`

    {
      "strokes": [
        { "points": [[x,y]], "color": "#FF0000", "width": 5, "isEraser": false }
      ],
      "shapes": [
        { "type": "rectangle", "startX": 50, "startY": 50,
          "endX": 150, "endY": 100, "color": "#0000FF", "strokeWidth": 4 }
      ],
      "texts": [
        { "text": "Hello!", "positionX": 300, "positionY": 400,
          "color": "#000000", "size": 48 }
      ]
    }

## 🚀 Setup & IFP Deployment

1. Clone the repo:

   git clone https://github.com/yourusername/whiteboard-app.git

2. Open in Android Studio
3. Connect IFP device via ADB
4. Run:

   ./gradlew installDebug

5. App launches in landscape mode automatically

## 📦 Sample Files

- `whiteboard_sample1.json` — Basic drawing with shapes and text
- `whiteboard_sample2.json` — Classroom lesson layout

##  Tech Stack

- Kotlin
- Android Canvas, Paint, Path
- ViewModel + StateFlow
- Hilt Dependency Injection
- Gson for JSON serialization
- Material Components