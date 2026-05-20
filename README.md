# FastScan - Android Document Scanner

FastScan is an Android document scanning application that captures photos, automatically crops documents, applies a real scanned-document effect, and compresses images to required size limits for Aadhaar cards, passport photos, and other document uploads.

---

## Features

- 📷 Capture image directly from camera
- ✂️ Auto crop document/photo
- 📄 Real scanner effect like CamScanner
- ⚡ Fast image processing
- 🧾 White background with dark readable text
- 🧹 Noise and shadow removal
- 🪪 Aadhaar card scan optimization
- 📉 Image compression under:
  - 20KB for photos
  - 100KB for Aadhaar/documents
- 💾 Save processed image to storage
- 🎨 Clean and simple Android UI

---

## Tech Stack

- Android Studio
- Java / Kotlin
- OpenCV
- Bitmap Image Processing
- CameraX / Camera2 API

---

## Scan Processing Flow

1. Capture Image
2. Detect Document Edges
3. Crop Perspective
4. Convert to Grayscale
5. Remove Noise
6. Enhance Contrast
7. Apply Adaptive Threshold
8. Compress Image
9. Save Final Output

---

## Scanner Effects Used

- Grayscale Conversion
- Adaptive Thresholding
- Noise Reduction
- Contrast Enhancement
- Perspective Crop

---

## Screenshots

> Add screenshots here

| Original | Cropped | Scanned Output |
|----------|----------|----------------|
| Image | Image | Image |

---

## Installation

### Clone Repository

```bash
git clone https://github.com/Santoshchach/fastscan.git
```

### Open in Android Studio

1. Open Android Studio
2. Click on **Open Project**
3. Select the cloned folder
4. Sync Gradle
5. Run the app

---

## Requirements

- Android Studio
- Android SDK
- OpenCV Android Library

---

## Future Improvements

- PDF Export
- OCR Text Recognition
- QR Code Detection
- Batch Scanning
- Dark Mode
- Auto Edge Detection using AI

---

## License

This project is licensed under the MIT License.

---

## Author

Developed by Santosh Chacharkar
