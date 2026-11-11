#!/usr/bin/env python3
"""Optional self-hosted yt-dlp backend for Musync (only used when configured in Settings)."""
import json
import subprocess
import urllib.parse
from http.server import BaseHTTPRequestHandler, HTTPServer

HOST = "0.0.0.0"
PORT = 8000


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        qs = urllib.parse.parse_qs(parsed.query)

        if parsed.path == "/search":
            query = qs.get("q", [""])[0].strip() or "trending music"
            cmd = [
                "yt-dlp", "--dump-json", "--flat-playlist", "--no-warnings",
                f"ytsearch15:{query}",
            ]
            try:
                out = subprocess.check_output(cmd, text=True, stderr=subprocess.DEVNULL)
            except Exception as e:
                self.send_error(500, str(e))
                return
            items = []
            for line in out.splitlines():
                line = line.strip()
                if not line:
                    continue
                try:
                    items.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
            self._json(items)
            return

        if parsed.path == "/resolve":
            video_id = qs.get("id", [""])[0].strip()
            if not video_id:
                self.send_error(400, "Missing id")
                return
            cmd = [
                "yt-dlp", "--dump-json", "--no-warnings", "-f", "ba[ext=m4a]/ba/b",
                f"https://www.youtube.com/watch?v={video_id}",
            ]
            try:
                out = subprocess.check_output(cmd, text=True, stderr=subprocess.DEVNULL)
                info = json.loads(out.splitlines()[0])
            except Exception as e:
                self.send_error(500, str(e))
                return
            self._json({
                "url": info.get("url", ""),
                "ext": info.get("ext", "m4a"),
            })
            return

        self.send_error(404)

    def _json(self, payload):
        body = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print(f"yt-dlp backend listening on http://{HOST}:{PORT}")
    HTTPServer((HOST, PORT), Handler).serve_forever()
