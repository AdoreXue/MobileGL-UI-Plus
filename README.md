<h1 align="center">MobileGL UI Plus</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Language-C%2B%2B%20%7C%20Kotlin-00599c?style=flat&logo=kotlin&logoColor=white" alt="C++ and Kotlin">
  <img src="https://img.shields.io/badge/License-GNU%20LGPL%203.0-00399c?style=flat" alt="GNU LGPL 3.0">
  <img src="https://img.shields.io/badge/Status-Release%20candidate-0078d7?style=flat" alt="Release candidate">
</p>

<p align="center"><em>
MobileGL on Android, with a configuration app
</em></p>

**This is a fork of [MobileGL-Dev/MobileGL](https://github.com/MobileGL-Dev/MobileGL).** The core
OpenGL implementation is upstream's and is kept in step with it; what this fork adds is
`android-plugin-ui/`, a Jetpack Compose app that configures the Android renderer plugin, imports
driver packages and libraries, and runs the driver self test. The core is not modified here beyond
merging upstream.

| | |
|---|---|
| **Download** | [Releases](https://github.com/AdoreXue/MobileGL-UI-Plus/releases): `MobileGL-<version>.apk`, release-signed |
| **What it is** | A renderer plugin for FCL, Zalith Launcher 2 and other Pojav-style launchers |
| **Set up the app** | [userguide/android-plugin-ui.md](userguide/android-plugin-ui.md) |
| **Adds over upstream** | The configuration UI ([screens and features](userguide/android-plugin-ui.md#what-the-app-shows)), imported driver packages and Vulkan ICDs, and the SFPEW wrapper switch |
| **Upstream user guide** | [userguide/README.md](userguide/README.md) |

### 这个仓库是什么

上游 [MobileGL-Dev/MobileGL](https://github.com/MobileGL-Dev/MobileGL) 的 Android 分支，核心 OpenGL
实现与上游同步、本身不改；本仓库加的是 `android-plugin-ui/` 这个配置 App：给 FCL、Zalith
Launcher 2 等启动器当 MobileGL 渲染器插件，在里面切换后端、改运行时配置、导入驱动包、跑驱动自检。
APK 在 [Releases](https://github.com/AdoreXue/MobileGL-UI-Plus/releases) 下载，安装和用法见
[中文说明](userguide/android-plugin-ui.md)。

---

The sections below describe the upstream project. MobileGL is a *free* and *open-source* project that implements a desktop **OpenGL** API. The goal is to provide a complete desktop OpenGL implementation with a state management layer and multi-backend support.

> [!NOTE]
>
> **Status:** In development. Both backends expose **OpenGL 4.6 (Core Profile)**; a small number of 4.6 features are still incomplete.

For end-user setup of the core (building, installing, configuring and troubleshooting on Android, Linux and Windows), see the [upstream user guide](userguide/README.md). For this fork's Android app, see [userguide/android-plugin-ui.md](userguide/android-plugin-ui.md).

## Project positioning

MobileGL is an implementation of a desktop OpenGL library. It aims to provide:

* Full OpenGL state management.
* A front-end that exposes OpenGL functions.
* Multiple independent backend implementations, where each backend targets a specific graphics API and remains fully isolated from others.

This project is intended as an implementation/translation layer.

## Key components

The repository is organized into following top-level modules:

1. **MG_State** — state tracking and management logic for Graphics APIs.
2. **MG_Impl** — front-end implementations of Graphics APIs that interact with `MG_State` and `MG_Backend`: OpenGL, plus the window-system APIs EGL, GLX, WGL, CGL and NSOpenGL.
3. **MG_Backend** — per-backend translation layer that maps front-end Graphics APIs' semantics and state into concrete backend API calls (e.g. OpenGL ES, Vulkan).
4. **MG_Pipe** and **MG_Remote** — the command stream between front-end and backend, and the client/server transport that lets the backend run in a separate process (see `docs/Disaggregated`).
5. **MG_Gbm** — a GBM backend whose buffers are images shared with the MobileGL server, so Wayland clients and compositors can exchange them as dma-bufs (RGBA, and NV12/P010 YUV where the server's platform allocates them). YUV dma-bufs - the server's own or a video decoder's - import through `EGL_EXT_image_dma_buf_import` and are sampled through `GL_OES_EGL_image_external(_essl3)`: the backend converts the image into the texture's RGBA level at its first use in each frame (zero-copy for a server image; a foreign buffer, which the platform cannot import, is first copied into one by the CPU).
6. **MG_Util** and other utility modules.

## Third-party components

MobileGL reuses several open-source projects:

* **SPIRV-Cross** by **KhronosGroup** - [Apache License 2.0](https://github.com/KhronosGroup/SPIRV-Cross/blob/master/LICENSE): [github](https://github.com/KhronosGroup/SPIRV-Cross)
* **glslang** by **KhronosGroup** - [Various Licenses](https://github.com/KhronosGroup/glslang/blob/main/LICENSE.txt): [github](https://github.com/KhronosGroup/glslang)
* **DiligentCore** by **Diligent Graphics** - [Apache License 2.0](https://github.com/DiligentGraphics/DiligentCore/blob/master/License.txt): [github](https://github.com/DiligentGraphics/DiligentCore)
* **flat_hash_map** by **Malte Skarupke** - [Boost Software License 1.0](https://github.com/MobileGL-Dev/flat_hash_map/blob/master/LICENSE): [github](https://github.com/MobileGL-Dev/flat_hash_map)

Refer to each component's repository for exact license texts. Any bundled third-party code in this repository is included under the upstream project's license.

## Compatibility & target

* **Target:** `OpenGL 4.6 (Core Profile)`, which both backends already report; the remaining work is closing the last 4.6 gaps and conformance.
* **Backends:**
  * `Direct (Vulkan)`
  * `Direct (OpenGL ES)`
* **Platforms:** Android, Linux (EGL/GLX, Wayland, usable as a glvnd vendor), Windows (WGL) and macOS (CGL/NSOpenGL).
* **Current development focus:**
  * Performance improvement
  * OpenGL 4.6 completeness and conformance
  * Disaggregated mode: the backend in a separate server process serving several clients, e.g. a full Linux desktop on Android with zero-copy window buffers

## Build Instructions

The core library has **no precompiled binaries**: build it from source. The Android app in this
fork does ship as an APK on the [releases page](https://github.com/AdoreXue/MobileGL-UI-Plus/releases),
and its own build is described in [userguide/android-plugin-ui.md](userguide/android-plugin-ui.md).
To build the core:

1. Clone the repository:

   ```sh
   git clone https://github.com/MobileGL-Dev/MobileGL.git
   ```

2. Initialize and update all submodules recursively:

   ```sh
   git submodule update --init --recursive
   ```

3. Follow glslang’s own documentation for its required initiation.

4. Configure and build the project with CMake:

   ```sh
   cmake -B build
   cmake --build build
   ```
   
   or do it in a modern way:
   
   ```sh
   cmake -S . -B build -G Ninja -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++
   cmake --build build
   ```
   
   Alternatively, you can use platform-specific build commands as needed.

### Build For macOS

On macOS, MobileGL can be built as a dylib that exposes the normal OpenGL/CGL/NSOpenGL entry points and routes them to the `DirectVulkan` backend. This is useful for running applications such as Minecraft through their stock GLFW/LWJGL OpenGL path while MobileGL is injected before context creation.

Prerequisites:

* macOS with Clang and Ninja.
* Vulkan loader and MoltenVK installed. With Homebrew, the MoltenVK ICD is commonly located at `/opt/homebrew/etc/vulkan/icd.d/MoltenVK_icd.json`.

Configure and build:

```sh
cmake -S . -B build-macos-magma \
  -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DMOBILEGL_BACKEND_TYPE=DirectVulkan \
  -DMOBILEGL_BUILD_TEST=OFF \
  -DMOBILEGL_BUILD_BENCHMARK=OFF

cmake --build build-macos-magma --target MobileGL -j8
```

The dylib will be generated at:

```sh
build-macos-magma/libMobileGL.dylib
```

To run Minecraft by MobileGL from a launcher like PrismLauncher, keep the stock LWJGL/GLFW natives and add a wrapper command to the instance settings:

```sh
env DYLD_INSERT_LIBRARIES=/absolute/path/to/MobileGL/build-macos-magma/libMobileGL.dylib MOBILEGL_BACKEND_TYPE=DirectVulkan VK_ICD_FILENAMES=/opt/homebrew/etc/vulkan/icd.d/MoltenVK_icd.json
```

Also make sure the JVM arguments include:

```sh
-XstartOnFirstThread
```

`DYLD_INSERT_LIBRARIES` must be active before GLFW creates its OpenGL context. After startup, the Minecraft F3 screen should report MobileGL and the `Direct (Vulkan)` backend if the injection worked.

## Build Options

| Option                       | Description                                           | Default |
|------------------------------| ----------------------------------------------------- | ------- |
| `MOBILEGL_BUILD_TEST`        | Build MobileGL tests (requires Clang)                 | ON      |
| `MOBILEGL_BUILD_BENCHMARK`   | Build MobileGL benchmarks (requires Clang)            | ON      |
| `MOBILEGL_ENABLE_LTO`        | ThinLTO (or the compiler's IPO) plus `-O3` and the release optimization flags. The Gradle (Android) build turns it on by default; pass `-Pmobilegl.enableLto=OFF` to disable it | OFF     |
| `MOBILEGL_FORCE_RELEASE_OPT` | Also apply the `MOBILEGL_ENABLE_LTO` flags in Debug builds. Has no effect unless `MOBILEGL_ENABLE_LTO=ON` | ON      |
| `MOBILEGL_ENABLE_TRACY`      | Enable Tracy profiler for performance analysis        | OFF     |

   **Notes:**

* The project requires C++23.
* `MG_Test` and `MG_Benchmark` can only be built with Clang, not GCC. To enforce Clang, add `-DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++` to your command.
* On Android, tests and benchmarks are always disabled.

## Environment Variables

MobileGL supports runtime configuration via environment variables.

### Supported Keys

| Variable                | Description                                      | Allowed Values                       | Default        |
|-------------------------|--------------------------------------------------|--------------------------------------|----------------|
| `MOBILEGL_BACKEND_TYPE` | Select active backend implementation at startup. | `DirectGLES`, `DirectVulkan`         | `DirectGLES`   |
| `MOBILEGL_DISABLE_TIMERQUERY` | Disable GPU timer-query exposure and use. | `0`, `1` | `0` |
| `MOBILEGL_ESPRYT_USE_ANGLE` | Load ANGLE EGL/GLES libraries. | `0`, `1` | `0` |
| `MOBILEGL_SUBGROUP_EMULATION` | `GL_KHR_shader_subgroup` emulation (32-lane, compute stage). `Auto` emulates only where the driver has no subgroup operations; `ForceOn` emulates even over native support; `ForceOff` never emulates (native support is still used). | `Auto`, `ForceOn`, `ForceOff` | `Auto` |
| `MOBILEGL_ITRP_FIX` | The iterationRP shaderpack repairs (scratch-array size, missing workgroup barrier); fingerprint-gated, other shaders are untouched. | `Auto`, `ForceOn`, `ForceOff` | `Auto` |
| `MOBILEGL_ADVERTISE_FP64` | Advertise `GL_ARB_gpu_shader_fp64`. GLSL `double`/`dvec`/`dmat` compile and run either way - they are narrowed to 32 bits - so this only changes whether an application is told it has 64-bit precision, which it does not. | `0`, `1` | `0` |
| `MOBILEGL_MAGMA_R11G11B10F_FALLBACK` | Use Magma's R11G11B10F format fallback. | `0`, `1` | `0` |
| `MOBILEGL_MAGMA_ALLOW_INVALID_SPIRV` | Hand a shader module that fails SPIR-V validation to the driver anyway. By default Magma declines such a program (no shader module is created, its draws and dispatches are refused and logged by name), because a driver that accepts invalid SPIR-V can fault the GPU into device loss. Diagnostic use only. | `0`, `1` | `0` |
| `MOBILEGL_FRAMES_IN_FLIGHT` | How many frames may be queued ahead of the GPU (Magma: its frame contexts; Espryt: how far presents may run ahead). The old name `MOBILEGL_MAGMA_FRAMESINFLIGHT` still works but is deprecated. | Integer `1`–`64` | `3` |
| `MOBILEGL_MAGMA_MAX_DRAWS_PER_COMMAND_BUFFER` | Draws and dispatches Magma records into one command buffer before it submits the buffer mid-frame and continues on a fresh one. `0` never splits. | Integer `0`–`16777216` | `16384` |
| `MOBILEGL_MAGMA_DESCRIPTOR_TRIM_FRAMES` | Frames a frame slot's descriptor pools must stay under a quarter full before Magma frees the slot's cached descriptor sets and grown pools. `0` never trims. | Integer `0`–`1048576` | `120` |
| `MOBILEGL_MAGMA_EXTRA_SWAPCHAIN_IMAGES` | Swapchain images Magma asks for beyond the surface's minimum, capped at its maximum. More images delay the reuse of the window buffer the display has just released, so fewer frames wait for its release; each costs a full-size color buffer and a depth/stencil buffer. | Integer `0`–`16` | `0` |
| `MOBILEGL_ESPRYT_AVOID_SAMPLER_MIPMAP_MIN_FILTER` | Avoid sampler mipmap minification filters. | `0`, `1` | `0` |
| `MOBILEGL_COHERENT_AS_FLUSH` | Treat persistent `GL_MAP_FLUSH_EXPLICIT_BIT` maps as coherent (app-compat for engines like Flywheel that never flush them). | `0`, `1` | `0` |
| `MOBILEGL_ESPRYT_FORCE_DS_READBACK_EMULATION` | Always emulate depth/stencil `glReadPixels`/`glGetTexImage` by shader sampling on Espryt, instead of using the driver's own depth/stencil readback where it has one. | `0`, `1` | `0` |
| `MOBILEGL_YUV_DRIVER_CONVERSION` | Espryt: convert YUV images with the driver's external-sampler conversion (its own color space) instead of `GL_EXT_YUV_target` with the import's `EGL_YUV_COLOR_SPACE_HINT_EXT` / `EGL_SAMPLE_RANGE_HINT_EXT`. | `0`, `1` | `0` |
| `MOBILEGL_SERVER_FRAME_STATS` | Log one `P65ServerFrame` timing line per presented frame on the server (apply vs. wall time, socket read rate). On Android also `debug.mobilegl.server_frame_stats`. | `0`, `1` | `0` |
| `VK_ICD_FILENAMES`      | Select the Vulkan ICD used by the Vulkan loader. | Path to an ICD JSON file             | Loader default |

## License

This project is distributed under **GNU LGPL v3.0**. See the `LICENSE` file in the repository for detailed information.
