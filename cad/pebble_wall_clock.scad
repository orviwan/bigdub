/*
 * ============================================================================
 * Giant Pebble Smartwatch Wall Clock for Nook Simple Touch (BNRV300)
 * Designed by orviwan
 * ============================================================================
 * Parametric OpenSCAD Model
 * Generates Front Bezel, Rear Mount, Button Caps, Desk Stand, and Split Parts.
 * ============================================================================
 */

// ============================================================================
// CUSTOMIZER PARAMETERS
// ============================================================================

/* [Part Selection] */
// Which part to render:
part = "assembly"; // [assembly: Complete Assembly Preview, front_bezel: Front Bezel (Printable), rear_mount: Rear Wall Mount (Printable), button_caps: Side Button Caps (Printable), desk_stand: Desktop Kickstand (Printable), split_front_top: Split Front Top Half (Ender 3 / Mini), split_front_bottom: Split Front Bottom Half (Ender 3 / Mini), split_rear_top: Split Rear Top Half, split_rear_bottom: Split Rear Bottom Half]

/* [Nook Simple Touch Dimensions] */
nook_w = 127.0;       // Nook width (mm)
nook_h = 165.0;       // Nook height (mm)
nook_d = 12.0;        // Nook thickness (mm)
nook_clr = 1.2;       // Fit clearance (0.5 to 1.5mm recommended)

/* [E-Ink Display Active Window] */
screen_w = 91.5;      // Screen cutout width (6.0" Pearl: 90.6mm active + margin)
screen_h = 123.5;     // Screen cutout height (122.4mm active + margin)
screen_offset_y = 0.5;// Slight vertical offset alignment

/* [Pebble Case Styling] */
bezel_border_x = 14.0;// Side bezel thickness
bezel_border_y = 16.0;// Top/bottom bezel thickness
body_corner_r = 18.0; // Outer rounded corner radius
front_chamfer = 3.0;  // Front edge chamfer
front_thickness = 9.0;// Front bezel total thickness
rear_thickness = 15.0;// Rear mount total thickness

/* [Strap Lugs] */
lug_extension = 22.0; // How far lugs protrude from top/bottom
lug_horn_w = 18.0;    // Width of each lug horn
lug_spacing = 72.0;   // Gap between lug horns (strap slot)
lug_hole_dia = 5.0;   // Cross hole for strap pin / mounting dowel

/* [Fasteners & Wall Mount] */
screw_dia = 3.2;      // M3 clearance hole diameter
screw_head_dia = 6.2; // M3 socket/pan head counterbore diameter
screw_inset = 9.0;    // Screw distance from outer edges
keyhole_large_dia = 9.5; // Wall screw entry hole
keyhole_small_dia = 4.5; // Wall screw shank slot width
cable_channel_w = 16.0;  // Micro-USB cable route width
cable_channel_d = 8.5;   // Micro-USB cable route depth

$fn = 64;

// Derived Dimensions
body_w = nook_w + nook_clr + (bezel_border_x * 2);
body_h = nook_h + nook_clr + (bezel_border_y * 2);
pocket_w = nook_w + nook_clr;
pocket_h = nook_h + nook_clr;
pocket_d = nook_d + 0.8;

// ============================================================================
// HELPER MODULES
// ============================================================================

// 2D Rounded Rectangle centered at origin
module rounded_rect_2d(w, h, r) {
    hull() {
        translate([-w/2 + r, -h/2 + r]) circle(r=r);
        translate([ w/2 - r, -h/2 + r]) circle(r=r);
        translate([-w/2 + r,  h/2 - r]) circle(r=r);
        translate([ w/2 - r,  h/2 - r]) circle(r=r);
    }
}

// 3D Rounded Block with chamfered top
module pebble_body_blank(w, h, depth, r, chamfer) {
    hull() {
        // Base flat
        linear_extrude(height = 1)
            rounded_rect_2d(w, h, r);
        // Mid body
        translate([0, 0, depth - chamfer])
            linear_extrude(height = 0.1)
                rounded_rect_2d(w, h, r);
        // Chamfered / rounded top
        translate([0, 0, depth])
            linear_extrude(height = 0.1)
                rounded_rect_2d(w - (chamfer*2), h - (chamfer*2), max(1, r - chamfer));
    }
}

// Keyhole Slot for hanging on wall screws
module keyhole_slot(depth=10) {
    union() {
        // Large entry hole for screw head
        cylinder(h=depth+2, d=keyhole_large_dia, center=true);
        // Narrow slot for screw shank
        translate([0, 10, 0])
            cube([keyhole_small_dia, 20, depth+2], center=true);
        // Undercut pocket for screw head inside wall
        translate([0, 5, 2])
            cylinder(h=depth, d=keyhole_large_dia, center=false);
        translate([0, 10, 2])
            cube([keyhole_large_dia, 12, depth], center=false);
    }
}

// Lugs at Top and Bottom
module strap_lugs(depth) {
    for (dir = [-1, 1]) {
        translate([0, dir * (body_h/2 + lug_extension/2 - 1), depth/2]) {
            difference() {
                // Outer lug volume
                hull() {
                    translate([-lug_spacing/2 - lug_horn_w/2, 0, 0])
                        cube([lug_horn_w, lug_extension + 2, depth], center=true);
                    translate([ lug_spacing/2 + lug_horn_w/2, 0, 0])
                        cube([lug_horn_w, lug_extension + 2, depth], center=true);
                }
                // Center cutout between horns
                cube([lug_spacing, lug_extension + 6, depth + 2], center=true);
                
                // Cross pin holes for strap / rod
                rotate([0, 90, 0])
                    cylinder(h=body_w + 10, d=lug_hole_dia, center=true);
            }
        }
    }
}

// Pebble Side Buttons Geometry
module pebble_buttons_geometry(depth) {
    btn_w = 6.0;
    btn_l = 22.0;
    btn_h = depth * 0.65;
    
    // Left: 1 Back button
    translate([-body_w/2 - btn_w/2 + 0.5, 0, depth/2]) {
        hull() {
            cube([btn_w, btn_l - 4, btn_h], center=true);
            translate([btn_w/2, 0, 0])
                cube([0.1, btn_l, btn_h - 2], center=true);
        }
    }
    
    // Right: 3 buttons (Up, Select, Down)
    right_y_offsets = [36.0, 0.0, -36.0];
    for (y_off = right_y_offsets) {
        translate([body_w/2 + btn_w/2 - 0.5, y_off, depth/2]) {
            hull() {
                cube([btn_w, btn_l - 4, btn_h], center=true);
                translate([-btn_w/2, 0, 0])
                    cube([0.1, btn_l, btn_h - 2], center=true);
            }
        }
    }
}

// Side button sockets in chassis
module side_button_sockets(depth) {
    btn_w = 7.0;
    btn_l = 24.0;
    btn_h = depth * 0.75;
    
    // Left socket
    translate([-body_w/2, 0, depth/2])
        cube([btn_w * 2, btn_l, btn_h], center=true);
        
    // Right sockets
    for (y_off = [36.0, 0.0, -36.0]) {
        translate([body_w/2, y_off, depth/2])
            cube([btn_w * 2, btn_l, btn_h], center=true);
    }
}

// Corner screw holes
module corner_screws(depth, head_side="front") {
    x_pos = body_w/2 - screw_inset;
    y_pos = body_h/2 - screw_inset;
    
    for (sx = [-1, 1]) {
        for (sy = [-1, 1]) {
            translate([sx * x_pos, sy * y_pos, -1]) {
                // Through hole
                cylinder(h=depth + 4, d=screw_dia);
                // Counterbore
                if (head_side == "front") {
                    translate([0, 0, depth - 4])
                        cylinder(h=10, d=screw_head_dia);
                } else if (head_side == "rear") {
                    translate([0, 0, -2])
                        cylinder(h=6, d=screw_head_dia);
                }
            }
        }
    }
}

// ============================================================================
// COMPONENT 1: FRONT BEZEL
// ============================================================================
module front_bezel() {
    difference() {
        union() {
            // Main Watch Case Body
            pebble_body_blank(body_w, body_h, front_thickness, body_corner_r, front_chamfer);
            // Lugs integrated to front
            strap_lugs(front_thickness);
        }
        
        // Active Screen Cutout (Bezel Window) with chamfer
        translate([0, screen_offset_y, -1]) {
            // Straight through cutout
            cube([screen_w, screen_h, front_thickness + 2], center=true);
            
            // Beveled screen frame
            translate([0, 0, front_thickness - 1.5])
                hull() {
                    cube([screen_w, screen_h, 0.1], center=true);
                    translate([0, 0, 3])
                        cube([screen_w + 5, screen_h + 5, 0.1], center=true);
                }
        }
        
        // Embossed Pebble logo recess below screen
        translate([0, -body_h/2 + 8, front_thickness - 0.6]) {
            linear_extrude(height=1)
                text("pebble", size=7.5, font="Liberation Sans:style=Bold", halign="center", valign="center");
        }
        
        // Sockets for side button caps
        side_button_sockets(front_thickness);
        
        // Screw holes (countersunk from front)
        corner_screws(front_thickness, head_side="front");
        
        // Inner lip to seat Nook glass perimeter flush
        translate([0, 0, -0.5])
            cube([pocket_w, pocket_h, 2.5], center=true);
    }
}

// ============================================================================
// COMPONENT 2: REAR WALL MOUNT & CHASSIS
// ============================================================================
module rear_mount() {
    difference() {
        union() {
            // Main Back Housing Body
            pebble_body_blank(body_w, body_h, rear_thickness, body_corner_r, 1.5);
            // Lugs integrated to rear
            strap_lugs(rear_thickness);
        }
        
        // Pocket for Nook Simple Touch
        translate([0, 0, rear_thickness - pocket_d + 0.1]) {
            cube([pocket_w, pocket_h, pocket_d + 2], center=true);
        }
        
        // Micro-USB Cable Channel running out the bottom & through back
        translate([0, -body_h/2, rear_thickness - pocket_d/2]) {
            // Channel out bottom edge
            cube([cable_channel_w, body_h/2, cable_channel_d], center=true);
        }
        translate([0, -nook_h/2 + 10, -1]) {
            // Rear cable passthrough slot for in-wall / flat wiring
            cube([cable_channel_w, 28, rear_thickness + 2], center=true);
        }
        
        // Power Button Finger Access on Top-Rear
        translate([24, body_h/2 - 8, -1]) {
            hull() {
                cylinder(h=rear_thickness + 2, d=16);
                translate([0, 10, 0])
                    cylinder(h=rear_thickness + 2, d=16);
            }
        }
        
        // Corner screw pilot holes (for M3 threaded inserts or direct thread)
        corner_screws(rear_thickness, head_side="none");
        
        // Keyhole Slots for Wall Mounting (Upper and Lower)
        translate([0, 45, 0])
            keyhole_slot(depth=rear_thickness - pocket_d);
        translate([0, -45, 0])
            keyhole_slot(depth=rear_thickness - pocket_d);
            
        // 3M Command Strip Recessed Bonding Flats (25 x 75 mm, 0.6mm deep)
        for (sx = [-1, 1]) {
            translate([sx * 46, 0, -0.1])
                cube([26, 76, 0.8], center=true);
        }
    }
}

// ============================================================================
// COMPONENT 3: PEBBLE BUTTON CAPS
// ============================================================================
module button_caps() {
    // Generates 4 button caps on a sprue or layout for printing
    btn_w = 5.6;
    btn_l = 21.0;
    btn_h = 9.0;
    
    for (i = [0:3]) {
        translate([i * 12 - 18, 0, btn_h/2]) {
            difference() {
                hull() {
                    cube([btn_w, btn_l - 4, btn_h], center=true);
                    translate([0, 0, btn_h/2])
                        cube([btn_w - 1.5, btn_l, 0.1], center=true);
                }
                // Finger grip indentation / groove
                translate([0, 0, btn_h/2 + 1])
                    rotate([0, 90, 0])
                        cylinder(h=btn_w+2, r=6, center=true);
            }
        }
    }
}

// ============================================================================
// COMPONENT 4: DESK KICKSTAND (18° Tilt)
// ============================================================================
module desk_stand() {
    stand_w = lug_spacing - 1.0; // Fits inside lower lug gap
    stand_l = 85.0;
    stand_t = 8.0;
    angle = 18.0;
    
    rotate([angle, 0, 0]) {
        difference() {
            union() {
                // Main kickstand foot
                hull() {
                    translate([0, -stand_l/2, stand_t/2])
                        cube([stand_w, stand_l, stand_t], center=true);
                    translate([0, 0, 10])
                        cube([stand_w, 16, 20], center=true);
                }
                // Lug engagement pins
                rotate([0, 90, 0])
                    cylinder(h=lug_spacing + 16, d=lug_hole_dia - 0.4, center=true);
            }
            // Weight reduction / cable pass cutouts
            translate([0, -stand_l/2, stand_t/2])
                cube([stand_w - 18, stand_l - 24, stand_t + 4], center=true);
        }
    }
}

// ============================================================================
// SPLIT PIECES FOR SMALLER PRINTER BEDS (Ender 3 / Prusa Mini / A1 Mini)
// ============================================================================
module split_front_top() {
    intersection() {
        front_bezel();
        translate([0, body_h/2, 0])
            cube([body_w + 60, body_h + 60, 50], center=true);
    }
}

module split_front_bottom() {
    intersection() {
        front_bezel();
        translate([0, -body_h/2, 0])
            cube([body_w + 60, body_h + 60, 50], center=true);
    }
}

module split_rear_top() {
    intersection() {
        rear_mount();
        translate([0, body_h/2, 0])
            cube([body_w + 60, body_h + 60, 50], center=true);
    }
}

module split_rear_bottom() {
    intersection() {
        rear_mount();
        translate([0, -body_h/2, 0])
            cube([body_w + 60, body_h + 60, 50], center=true);
    }
}

// ============================================================================
// ASSEMBLY RENDER
// ============================================================================
module complete_assembly() {
    // Front Bezel (Black / Red / White Pebble Body)
    color([0.15, 0.15, 0.15, 0.95])
        front_bezel();
        
    // Simulated Nook Simple Touch Screen inside
    color([0.88, 0.88, 0.85, 1.0])
        translate([0, screen_offset_y, -1.0])
            cube([screen_w, screen_h, 1.0], center=true);
            
    // Side Buttons (Brushed Silver / Dark Slate)
    color([0.65, 0.65, 0.70])
        pebble_buttons_geometry(front_thickness);
        
    // Rear Mount (Dark Gray)
    color([0.25, 0.25, 0.28, 0.9])
        translate([0, 0, -rear_thickness])
            rear_mount();
}

// ============================================================================
// MAIN EXECUTION SELECTOR
// ============================================================================
if (part == "assembly") {
    complete_assembly();
} else if (part == "front_bezel") {
    // Orient flat for 3D printing
    rotate([180, 0, 0])
        front_bezel();
} else if (part == "rear_mount") {
    // Orient flat for 3D printing
    rear_mount();
} else if (part == "button_caps") {
    button_caps();
} else if (part == "desk_stand") {
    desk_stand();
} else if (part == "split_front_top") {
    rotate([180, 0, 0]) split_front_top();
} else if (part == "split_front_bottom") {
    rotate([180, 0, 0]) split_front_bottom();
} else if (part == "split_rear_top") {
    split_rear_top();
} else if (part == "split_rear_bottom") {
    split_rear_bottom();
}
