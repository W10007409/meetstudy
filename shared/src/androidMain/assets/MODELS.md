# On-device models

## mobile_face_net.tflite

- **What:** MobileFaceNet face embedding, float32 TFLite export by Qualcomm AI Hub (`qai-hub-models` v0.66.0).
- **Download URL:** https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/mobile_facenet/releases/v0.66.0/mobile_facenet-tflite-float.zip
  (zip contains `mobile_facenet.tflite`, renamed here to `mobile_face_net.tflite`, and `metadata.json`).
- **Model card:** https://huggingface.co/qualcomm/MobileFaceNet
- **License:**
  - Model (as published by Qualcomm): Apache-2.0 (model card "License: apache-2.0").
  - Original weights `mobilefacenet.pt` from https://github.com/foamliu/MobileFaceNet: Apache-2.0,
    https://github.com/foamliu/MobileFaceNet/blob/master/LICENSE
  - Export wrapper code (`qai_hub_models/models/mobile_facenet/model.py`): BSD-3-Clause.
- **SHA-256:** `2046ab776fba2ec7e420767ffe7eb74a7402a8412d3d7ea8b9f4ca8420e5f3d7`
- **Inputs:** two image tensors `img1`, `img2`, float32, RGB, values in **[0, 1]**, shape per metadata `[1, 3, 112, 112]`
  (the app reads the real shapes from the interpreter at load and supports NCHW or NHWC; see logcat tag `MeetStudyFace`).
- **Normalization:** done inside the model (torchvision mean/std), plus test-time flip averaging. Feed raw RGB / 255.
- **Output:** `embeddings` float32 `[2, 128]` — row 0 is the embedding of `img1`, row 1 of `img2`.
  The app feeds the same crop to both inputs and uses row 0 (128-d, not 192-d).
- **Note:** our crop is an upright bounding-box crop, not landmark-aligned as in training; similarity values
  are expected to be lower than LFW numbers. Tune `IDENTITY_THRESHOLD` on device.
