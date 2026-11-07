#!/usr/bin/env python3
"""
Blender Scene Builder for Giant Pebble 91Dub Wall Clock
Imports STLs, sets up PBR materials, maps E-Ink screen texture, configures lighting & camera,
and can either save to .blend or send directly to a running Blender instance via MCP socket (port 9876).
"""

import sys
import os
import socket
import json

def generate_blender_code(repo_dir):
    stls_dir = os.path.join(repo_dir, "cad", "stls")
    texture_path = os.path.join(repo_dir, "cad", "pebble_screen_texture.png")
    
    code = f'''
import bpy
import math

# Clear existing objects
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)

# Helper to create PBR material
def make_mat(name, color, roughness=0.5, metallic=0.0):
    mat = bpy.data.materials.new(name=name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    bsdf = nodes.get("Principled BSDF")
    if bsdf:
        bsdf.inputs['Base Color'].default_value = color
        bsdf.inputs['Roughness'].default_value = roughness
        bsdf.inputs['Metallic'].default_value = metallic
    return mat

# Materials
mat_case = make_mat("Pebble_Body_Black", (0.05, 0.05, 0.06, 1.0), roughness=0.35, metallic=0.05)
mat_rear = make_mat("Pebble_Rear_Mount", (0.08, 0.08, 0.09, 1.0), roughness=0.5, metallic=0.0)
mat_buttons = make_mat("Pebble_Buttons", (0.35, 0.36, 0.38, 1.0), roughness=0.25, metallic=0.85)

# Import Front Bezel
front_path = r"{os.path.join(stls_dir, 'pebble_clock_front_bezel.stl')}"
bpy.ops.wm.stl_import(filepath=front_path)
obj_front = bpy.context.selected_objects[0]
obj_front.name = "Pebble_Front_Bezel"
obj_front.location = (0, 0, 0)
if obj_front.data.materials:
    obj_front.data.materials[0] = mat_case
else:
    obj_front.data.materials.append(mat_case)

# Import Rear Mount
rear_path = r"{os.path.join(stls_dir, 'pebble_clock_rear_mount.stl')}"
bpy.ops.wm.stl_import(filepath=rear_path)
obj_rear = bpy.context.selected_objects[0]
obj_rear.name = "Pebble_Rear_Mount"
obj_rear.location = (0, 0, -15.0)
if obj_rear.data.materials:
    obj_rear.data.materials[0] = mat_rear
else:
    obj_rear.data.materials.append(mat_rear)

# Import Button Caps
caps_path = r"{os.path.join(stls_dir, 'pebble_clock_button_caps.stl')}"
bpy.ops.wm.stl_import(filepath=caps_path)
obj_caps = bpy.context.selected_objects[0]
obj_caps.name = "Pebble_Button_Caps"
obj_caps.location = (120.0, 0, 0)
if obj_caps.data.materials:
    obj_caps.data.materials[0] = mat_buttons
else:
    obj_caps.data.materials.append(mat_buttons)

# Import Desk Stand
stand_path = r"{os.path.join(stls_dir, 'pebble_clock_desk_stand.stl')}"
bpy.ops.wm.stl_import(filepath=stand_path)
obj_stand = bpy.context.selected_objects[0]
obj_stand.name = "Pebble_Desk_Stand"
obj_stand.location = (-140.0, 0, 0)
if obj_stand.data.materials:
    obj_stand.data.materials[0] = mat_case
else:
    obj_stand.data.materials.append(mat_case)

# Create E-Ink Screen Plane (6" display = 90.6mm x 122.4mm)
bpy.ops.mesh.primitive_plane_add(size=1.0, location=(0, 0, 1.0))
screen_plane = bpy.context.active_object
screen_plane.name = "EInk_Display_91Dub"
screen_plane.scale = (91.5, 123.5, 1.0)
bpy.ops.object.transform_apply(scale=True)

# Screen Material with 91Dub Texture
screen_mat = bpy.data.materials.new(name="EInk_91Dub_Texture")
screen_mat.use_nodes = True
s_nodes = screen_mat.node_tree.nodes
s_links = screen_mat.node_tree.links
s_bsdf = s_nodes.get("Principled BSDF")

tex_image = s_nodes.new('ShaderNodeTexImage')
tex_image.location = (-300, 300)
try:
    img = bpy.data.images.load(r"{texture_path}")
    tex_image.image = img
    tex_image.interpolation = 'Closest' # Crisp e-ink pixels!
    s_links.new(tex_image.outputs['Color'], s_bsdf.inputs['Base Color'])
    s_bsdf.inputs['Roughness'].default_value = 0.8
except Exception as e:
    print("Texture load error:", e)

if screen_plane.data.materials:
    screen_plane.data.materials[0] = screen_mat
else:
    screen_plane.data.materials.append(screen_mat)

# Studio Lights
# Key light
bpy.ops.object.light_add(type='AREA', location=(150, -200, 250))
key_light = bpy.context.active_object
key_light.name = "Key_Light"
key_light.data.energy = 150.0
key_light.data.size = 200.0

# Fill light
bpy.ops.object.light_add(type='AREA', location=(-200, -150, 180))
fill_light = bpy.context.active_object
fill_light.name = "Fill_Light"
fill_light.data.energy = 80.0
fill_light.data.size = 250.0

# Rim / Back light
bpy.ops.object.light_add(type='SPOT', location=(0, 250, 200))
rim_light = bpy.context.active_object
rim_light.name = "Rim_Light"
rim_light.data.energy = 200.0

# Camera
bpy.ops.object.camera_add(location=(0, -420, 320), rotation=(math.radians(52), 0, 0))
cam = bpy.context.active_object
cam.name = "Pebble_Clock_Camera"
bpy.context.scene.camera = cam

# Set render settings
bpy.context.scene.render.engine = 'CYCLES' if hasattr(bpy.context.scene.render, 'CYCLES') else 'BLENDER_EEVEE'
print("Pebble 91Dub scene built successfully!")
'''
    return code


def try_mcp_socket(code, host="localhost", port=9876):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(2.0)
        s.connect((host, port))
        
        # Format MCP for Blender execute_code payload
        payload = json.dumps({
            "type": "execute_code",
            "params": {
                "code": code
            }
        })
        s.sendall(payload.encode('utf-8'))
        
        resp = s.recv(8192)
        s.close()
        return True, json.loads(resp.decode('utf-8'))
    except Exception as e:
        return False, str(e)


def main():
    repo_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    code = generate_blender_code(repo_dir)

    print("Checking if Blender MCP is listening on localhost:9876...")
    success, resp = try_mcp_socket(code)
    
    if success:
        print(" Successfully connected to running Blender instance via MCP socket!")
        print("Sent scene load command to Blender.")
        print("Response:", resp)
    else:
        print(f"Could not connect to live Blender socket on port 9876: {resp}")
        print("Generating cad/pebble_wall_clock.blend directly using Blender CLI...")
        
        script_file = os.path.join(repo_dir, "cad", "_temp_build_scene.py")
        blend_file = os.path.join(repo_dir, "cad", "pebble_wall_clock.blend")
        
        full_code = code + f"\nbpy.ops.wm.save_as_mainfile(filepath=r'{blend_file}')\n"
        with open(script_file, "w") as f:
            f.write(full_code)
            
        import subprocess
        res = subprocess.run(["blender", "-b", "--python", script_file], capture_output=True, text=True)
        if os.path.exists(script_file):
            os.remove(script_file)
            
        if os.path.exists(blend_file):
            print(f" Created {blend_file} ({os.path.getsize(blend_file)/1024:.1f} KB)!")
        else:
            print("Error creating blend file:", res.stderr)

if __name__ == '__main__':
    main()
