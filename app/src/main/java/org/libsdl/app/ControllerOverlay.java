package org.libsdl.app;

import android.content.Context;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import org.ikemen_engine.ikemen_go.R;

import java.util.HashMap;
import java.util.Map;

public class ControllerOverlay extends RelativeLayout {
    private int virtualDeviceId;
    private int hatX, hatY = 0;
    private final Map<Integer, Integer> pointerStates = new HashMap<>(); // PointerID -> Keycode/Axis
    private boolean isInitialized = false;

    public int getPhysicalJoystickCount() {
        int count = 0;
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int id : deviceIds) {
            InputDevice dev = InputDevice.getDevice(id);
            int sources = dev.getSources();
            // Check if the device is a physical joystick or gamepad
            if (((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) ||
                    ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)) {
                count++;
            }
        }
        return count;
    }

    private void setupButton(View parent, int viewId, final int androidButtonId) {
        View btn = parent.findViewById(viewId);
        if (btn == null) return;

        btn.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                SDLControllerManager.onNativePadDown(virtualDeviceId, androidButtonId);
            } else if (action == MotionEvent.ACTION_UP) {
                SDLControllerManager.onNativePadUp(virtualDeviceId, androidButtonId);
            }
            return true;
        });
        btn.setOnHoverListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                SDLControllerManager.onNativePadDown(virtualDeviceId, androidButtonId);
            } else if (action == MotionEvent.ACTION_UP) {
                SDLControllerManager.onNativePadUp(virtualDeviceId, androidButtonId);
            }
            return true;
        });
    }

    private void setupTrigger(View parent, int viewId, final int sdlAxisIndex) {
        View btn = parent.findViewById(viewId);
        if (btn == null) return;

        btn.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                SDLControllerManager.onNativeJoy(virtualDeviceId, sdlAxisIndex, 1.0f);
                btn.setPressed(true);
            } else if (action == MotionEvent.ACTION_UP) {
                SDLControllerManager.onNativeJoy(virtualDeviceId, sdlAxisIndex, 0.0f);
                btn.setPressed(false);
            }
            return true;
        });
    }
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isInitialized) {
            initializeVirtualController();
            isInitialized = true;
        }
        return true;
    }

    private int leftJoyPointerId = -1;
    private int rightJoyPointerId = -1;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pId = event.getPointerId(index);

        // Get raw coordinates for global hit detection
        float x = event.getX(index);
        float y = event.getY(index);

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            // Check Joysticks first
            if (isViewAtLocation(findViewById(R.id.left_analog), x, y)) {
                leftJoyPointerId = pId;
            } else if (isViewAtLocation(findViewById(R.id.right_analog), x, y)) {
                rightJoyPointerId = pId;
            } else {
                // If not a stick, it's a button/dpad
                updatePointer(pId, getButtonAt(x, y));
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int movePId = event.getPointerId(i); // The stable ID

                // Get coordinates specifically for THIS pointer index
                float mx = event.getX(i);
                float my = event.getY(i);

                if (movePId == leftJoyPointerId) {
                    updateJoystickLogic(findViewById(R.id.left_analog), mx, my);
                } else if (movePId == rightJoyPointerId) {
                    updateJoystickLogic(findViewById(R.id.right_analog), mx, my);
                } else {
                    // Check if we are actually over a NEW button
                    int currentButton = getButtonAt(mx, my);
                    Integer lastButton = pointerStates.get(movePId);

                    // Only update if the button under THIS finger has actually changed
                    if (lastButton == null || currentButton != lastButton) {
                        updatePointer(movePId, currentButton);
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            if (pId == leftJoyPointerId) {
                resetJoystick(findViewById(R.id.left_analog));
                leftJoyPointerId = -1;
            } else if (pId == rightJoyPointerId) {
                resetJoystick(findViewById(R.id.right_analog));
                rightJoyPointerId = -1;
            } else {
                releasePointer(pId);
            }
        }

        updateDpadState(event);
        return true;
    }

    private void updateJoystickLogic(View v, float x, float y) {
        if (!(v instanceof JoystickOverlay)) return;
        JoystickOverlay joy = (JoystickOverlay) v;

        // LOCAL CENTER CALCULATION
        float centerX = v.getLeft() + (v.getWidth() / 2f);
        float centerY = v.getTop() + (v.getHeight() / 2f);
        float radius = v.getWidth() / 3f;

        float dx = x - centerX;
        float dy = y - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance > radius) {
            dx = (dx / distance) * radius;
            dy = (dy / distance) * radius;
        }

        joy.updateVisualPos((v.getWidth()/2f) + dx, (v.getHeight()/2f) + dy);
        joy.sendToSDL(dx / radius, dy / radius);
    }

    private void resetJoystick(View v) {
        if (!(v instanceof JoystickOverlay)) return;
        JoystickOverlay joy = (JoystickOverlay) v;
        joy.updateVisualPos(v.getWidth()/2f, v.getHeight()/2f);
        joy.sendToSDL(0, 0);
    }

    private void updatePointer(int pointerId, int newCode) {
        Integer oldCode = pointerStates.get(pointerId);
//        if (oldCode != null && oldCode == newCode) return;

        if (oldCode != null && oldCode != -1) handleInput(oldCode, false);
        if (newCode != -1) handleInput(newCode, true);

        pointerStates.put(pointerId, newCode);
    }

    private void releasePointer(int pointerId) {
        Integer lastCode = pointerStates.remove(pointerId);
        if (lastCode != null && lastCode != -1) handleInput(lastCode, false);
    }

    private void handleInput(final int code, boolean pressed) {
        if (code == -1) return;

        if (code >= 4000) { // TRIGGERS
            int axis = code - 4000;
            SDLControllerManager.onNativeJoy(virtualDeviceId, axis, pressed ? 1.0f : 0.0f);
        }
        else if (code >= 1000) {
            // D-PAD visuals only (Logic remains in updateDpadState)
        }
        else { // STANDARD BUTTONS
            if (pressed) {
                SDLControllerManager.onNativeHat(virtualDeviceId, 0, hatX, hatY);
                int result = SDLControllerManager.onNativePadDown(virtualDeviceId, code);
                if (result < 0) {
                    android.util.Log.e("SDL", "INPUT FAILURE: Device " + virtualDeviceId + " rejected button " + code);
                }
            } else {
                SDLControllerManager.onNativePadUp(virtualDeviceId, code);
            }
        }

        // Always update visuals
        updateButtonVisual(code, pressed);
    }

    private void updateButtonVisual(int code, boolean pressed) {
        int viewId = -1;
        switch (code) {
            case 96:  viewId = R.id.btn_a; break;
            case 97:  viewId = R.id.btn_b; break;
            case 99:  viewId = R.id.btn_x; break;
            case 100: viewId = R.id.btn_y; break;
            case 102: viewId = R.id.btn_d; break;
            case 103: viewId = R.id.btn_z; break;
            case 108: viewId = R.id.btn_start; break;
            case 109: viewId = R.id.btn_back; break;
            case 1001: viewId = R.id.dp_up; break;
            case 1002: viewId = R.id.dp_down; break;
            case 1003: viewId = R.id.dp_left; break;
            case 1004: viewId = R.id.dp_right; break;
//            case 1005: viewId = R.id.dp_upleft; break;
//            case 1006: viewId = R.id.dp_downright; break;
//            case 1007: viewId = R.id.dp_downleft; break;
//            case 1008: viewId = R.id.dp_upright; break;
            case 4004: viewId = R.id.btn_w; break;
            case 4005: viewId = R.id.btn_c; break;
        }

        if (viewId != -1) {
            View v = findViewById(viewId);
            if (v != null) v.setPressed(pressed);
        }
    }

    private void updateDpadState(MotionEvent event) {
        int newX = 0, newY = 0;
        int action = event.getActionMasked();

        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
            View container = findViewById(R.id.dpad_container);
            if (container == null) return;

            // Use Local coordinates to match event.getX()
            float centerX = container.getLeft() + (container.getWidth() / 2f);
            float centerY = container.getTop() + (container.getHeight() / 2f);
            float radius = container.getWidth() / 2f;

            for (int i = 0; i < event.getPointerCount(); i++) {
                if (action == MotionEvent.ACTION_POINTER_UP && i == event.getActionIndex()) continue;

                float x = event.getX(i);
                float y = event.getY(i);

                // Calculate distance from center (Pythagoras)
                float dx = x - centerX;
                float dy = y - centerY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                // Only process if the finger is actually touching the D-Pad area
                // (with  a small "deadzone" in the middle so neutral is easy to hit)
                if (distance > (radius * 0.2f) && distance < (radius * 1.5f)) {
                    // Calculate angle in degrees (0 to 360)
                    double angle = Math.toDegrees(Math.atan2(dy, dx));
                    if (angle < 0) angle += 360;

                    // Map angle to 8 directions with wider diagonals
                    // Cardinals get 30 degrees, Diagonals get 60 degrees
                    if (angle >= 345 || angle < 15)        newX = 1;  // Right (Narrower)
                    else if (angle >= 15  && angle < 75)   { newX = 1; newY = 1; }   // Down-Right (Wider)
                    else if (angle >= 75  && angle < 105)  newY = 1; // Down
                    else if (angle >= 105 && angle < 165)  { newX = -1; newY = 1; }  // Down-Left
                    else if (angle >= 165 && angle < 195)  newX = -1; // Left
                    else if (angle >= 195 && angle < 255)  { newX = -1; newY = -1; } // Up-Left
                    else if (angle >= 255 && angle < 285)  newY = -1; // Up
                    else if (angle >= 285 && angle < 345)  { newX = 1; newY = -1; }  // Up-Right

                    // Map angle to 8 directions
//                    if (angle >= 337.5 || angle < 22.5)   newX = 1;  // Right
//                    else if (angle >= 22.5  && angle < 67.5)  { newX = 1; newY = 1; }   // Down-Right
//                    else if (angle >= 67.5  && angle < 112.5) newY = 1; // Down
//                    else if (angle >= 112.5 && angle < 157.5) { newX = -1; newY = 1; }  // Down-Left
//                    else if (angle >= 157.5 && angle < 202.5) newX = -1; // Left
//                    else if (angle >= 202.5 && angle < 247.5) { newX = -1; newY = -1; } // Up-Left
//                    else if (angle >= 247.5 && angle < 292.5) newY = -1; // Up
//                    else if (angle >= 292.5 && angle < 337.5) { newX = 1; newY = -1; }  // Up-Right

                    break; // One finger is enough for the D-pad
                }
            }
        }

        if (newX != hatX || newY != hatY) {
            hatX = newX;
            hatY = newY;
            SDLControllerManager.onNativeHat(virtualDeviceId, 0, hatX, hatY);
            updateDpadVisual(hatX, hatY);
        }
    }

    private int getButtonAt(float x, float y) {
        // Return -1 for analog sticks
        if (isViewAtLocation(findViewById(R.id.left_analog), x, y)) return -1;
        if (isViewAtLocation(findViewById(R.id.right_analog), x, y)) return -1;

        // Return -1 for D-Pad so it doesn't enter the pointerStates map logic
        if (isViewAtLocation(findViewById(R.id.dp_up), x, y)) return -1;
        if (isViewAtLocation(findViewById(R.id.dp_down), x, y)) return -1;
        if (isViewAtLocation(findViewById(R.id.dp_left), x, y)) return -1;
        if (isViewAtLocation(findViewById(R.id.dp_right), x, y)) return -1;
//        if (isViewAtLocation(findViewById(R.id.dp_upleft), x, y)) return -1;
//        if (isViewAtLocation(findViewById(R.id.dp_downright), x, y)) return -1;
//        if (isViewAtLocation(findViewById(R.id.dp_downleft), x, y)) return -1;
//        if (isViewAtLocation(findViewById(R.id.dp_upright), x, y)) return -1;

        // Physical Buttons
        if (isViewAtLocation(findViewById(R.id.btn_a), x, y)) return 96;
        if (isViewAtLocation(findViewById(R.id.btn_b), x, y)) return 97;
        if (isViewAtLocation(findViewById(R.id.btn_x), x, y)) return 99;
        if (isViewAtLocation(findViewById(R.id.btn_y), x, y)) return 100;
        if (isViewAtLocation(findViewById(R.id.btn_d), x, y)) return 102; // LB
        if (isViewAtLocation(findViewById(R.id.btn_z), x, y)) return 103; // RB
        if (isViewAtLocation(findViewById(R.id.btn_w), x, y)) return 4004; // LT
        if (isViewAtLocation(findViewById(R.id.btn_c), x, y)) return 4005; // RT
        if (isViewAtLocation(findViewById(R.id.btn_start), x, y)) return 108;
        if (isViewAtLocation(findViewById(R.id.btn_back), x, y)) return 109;
        return -1;
    }

    private void updateDpadVisual(int hX, int hY) {
        findViewById(R.id.dp_up).setPressed(hY == -1);
        findViewById(R.id.dp_down).setPressed(hY == 1);
        findViewById(R.id.dp_left).setPressed(hX == -1);
        findViewById(R.id.dp_right).setPressed(hX == 1);
    }

    // Helper to check if a finger is inside a specific button's area
    private boolean isViewAtLocation(View v, float x, float y) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;

        int[] vLoc = new int[2];
        v.getLocationOnScreen(vLoc); // Global position of button

        int[] parentLoc = new int[2];
        this.getLocationOnScreen(parentLoc); // Global position of the overlay

        // Get the position of the button RELATIVE to the overlay
        int relativeLeft = vLoc[0] - parentLoc[0];
        int relativeTop = vLoc[1] - parentLoc[1];

        return x >= relativeLeft && x <= (relativeLeft + v.getWidth()) &&
                y >= relativeTop && y <= (relativeTop + v.getHeight());
    }

    public ControllerOverlay(Context context) {
        super(context);
        this.setClickable(true);
        this.setFocusable(true);
        this.setEnabled(true);
        this.setMotionEventSplittingEnabled(true);

        // Inflate the buttons
        LayoutInflater inflater = LayoutInflater.from(context);
        View vc = inflater.inflate(R.layout.virtual_controller, this, true);
        disableAllTouches(vc);

        // Causes issues with updating state
//        setupDpad(vc);
//        setupButtons(vc);
    }

    private void disableAllTouches(View v) {
        v.setClickable(false);
        v.setFocusable(false);
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                disableAllTouches(vg.getChildAt(i));
            }
        }
    }

    public void initializeVirtualController() {
        android.util.Log.i("ControllerOverlay", "DEBUG: initializing virtual joystick...");
        virtualDeviceId = getPhysicalJoystickCount();
        pointerStates.clear();
        hatX = 0;
        hatY = 0;
        android.util.Log.i("ControllerOverlay", String.format("DEBUG: virtualDeviceID = %d", virtualDeviceId));
        SDLControllerManager.nativeRemoveJoystick(virtualDeviceId);
        ensureJoystickAlive();
        // Initialize the joysticks
        JoystickOverlay ls = findViewById(R.id.left_analog);
        ls.setAttrs(virtualDeviceId, 0, 1);

        JoystickOverlay rs = findViewById(R.id.right_analog);
        rs.setAttrs(virtualDeviceId, 2, 3);

        for (int i = 0; i < 6; i++) {
            SDLControllerManager.onNativeJoy(virtualDeviceId, i, 0.00390625f);
            SDLControllerManager.onNativeJoy(virtualDeviceId, i, 0.0f);
        }
        SDLControllerManager.onNativeHat(virtualDeviceId, 0, 0, 0);
        android.util.Log.i("ControllerOverlay", "DEBUG: virtual joystick initialized!");
        isInitialized = true;
    }

    private int ensureJoystickAlive() {
        return SDLControllerManager.nativeAddJoystick(virtualDeviceId, "Xbox 360 Controller", "Gamepad", 0x045E, 0x028E, false, 0xFFF, 6, 0x3F, 1, 0);
    }

    private void setupDpad(View vc) {
        vc.findViewById(R.id.dp_up).setClickable(false);
        vc.findViewById(R.id.dp_down).setClickable(false);
        vc.findViewById(R.id.dp_left).setClickable(false);
        vc.findViewById(R.id.dp_right).setClickable(false);
//        vc.findViewById(R.id.dp_upleft).setClickable(false);
//        vc.findViewById(R.id.dp_downright).setClickable(false);
//        vc.findViewById(R.id.dp_downleft).setClickable(false);
//        vc.findViewById(R.id.dp_upright).setClickable(false);
    }

    private void setupButtons(View vc) {
        setupButton(vc, R.id.btn_x, 99);      // X
        setupButton(vc, R.id.btn_y, 100);     // Y
        setupButton(vc, R.id.btn_z, 103);     // RB
        setupTrigger(vc, R.id.btn_w, 4);         // LT
        setupButton(vc, R.id.btn_a, 96);      // A
        setupButton(vc, R.id.btn_b, 97);      // B
        setupTrigger(vc, R.id.btn_c, 5);         // RT
        setupButton(vc, R.id.btn_d, 102);     // LB
        setupButton(vc, R.id.btn_back, 109);  // BACK
        setupButton(vc, R.id.btn_start, 108); // START
    }
}
