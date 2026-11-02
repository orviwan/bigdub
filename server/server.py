#!/usr/bin/env python3
"""
Lightweight HTTP Server for 91Dub E-Ink Clock
Serves static dashboard files with optimal e-ink caching headers.
Zero dependencies - runs anywhere Python 3 is installed.
"""

import os
import sys
from http.server import HTTPServer, SimpleHTTPRequestHandler

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
DIRECTORY = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'dashboard'))

class ClockRequestHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def end_headers(self):
        # Disable client caching so e-ink displays always receive fresh updates
        self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate')
        self.send_header('Pragma', 'no-cache')
        self.send_header('Expires', '0')
        self.send_header('Access-Control-Allow-Origin', '*')
        super().end_headers()

def run():
    server_address = ('0.0.0.0', PORT)
    httpd = HTTPServer(server_address, ClockRequestHandler)
    print(f"==================================================")
    print(f" Pebble 91Dub E-Ink Clock Server Running")
    print(f" Serving directory: {DIRECTORY}")
    print(f" Listening on:      http://0.0.0.0:{PORT}")
    print(f"==================================================")
    print(f" Point your Nook Simple Touch browser to:")
    print(f" http://<YOUR_SERVER_IP>:{PORT}/")
    print(f"==================================================")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...")
        httpd.server_close()

if __name__ == '__main__':
    run()
