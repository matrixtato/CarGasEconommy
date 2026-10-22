"""Servidor HTTPS para a PWA do CarGasEconommy (rede local).

PWAs exigem "contexto seguro" (HTTPS) para registrar Service Worker e
oferecer instalação — por isso o app roda em HTTPS com um certificado local
gerado por certs/make_cert.py. Como o celular ainda não confia nesse
certificado na primeira vez, um segundo servidor, em HTTP puro, roda junto
só para entregar o certificado (sem aviso de segurança).

Uso:
    python certs/make_cert.py       # uma vez, gera a CA e o certificado
    python server.py                # HTTPS na 8088 + HTTP (só do certificado) na 8089

No celular, uma vez só:
    1. Acesse http://<IP-deste-PC>:8089/rootCA.crt e instale o certificado.
    2. Acesse https://<IP-deste-PC>:8088 e instale o app (ou PC: --host nas duas portas).
"""
import argparse
import http.server
import socket
import socketserver
import ssl
import threading
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CERTS = ROOT / "certs"
SERVER_CERT = CERTS / "server.pem"
SERVER_KEY = CERTS / "server.key"
ROOT_CA = CERTS / "rootCA.crt"

NO_CACHE_FILES = {"/", "/index.html", "/sw.js", "/manifest.json"}

# Só o certificado público pode ser servido de certs/; a chave privada e o
# script gerador nunca devem ser expostos pela rede.
CERTS_ALLOWLIST = {"/certs/rootCA.crt"}


class PwaRequestHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        ".crt": "application/x-x509-ca-cert",
        ".json": "application/manifest+json",
    }

    def do_GET(self):
        if self.path.startswith("/certs/") and self.path not in CERTS_ALLOWLIST:
            self.send_error(403, "Forbidden")
            return
        super().do_GET()

    def end_headers(self):
        if self.path in NO_CACHE_FILES:
            self.send_header("Cache-Control", "no-cache, must-revalidate")
        super().end_headers()

    def log_message(self, format, *args):
        print("[pwa] " + (format % args))


class CertOnlyHandler(http.server.BaseHTTPRequestHandler):
    """Servidor HTTP puro minimalista: só entrega o certificado da CA."""

    def do_GET(self):
        if self.path in ("/", "/rootCA.crt"):
            data = ROOT_CA.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "application/x-x509-ca-cert")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        else:
            self.send_error(404)

    def log_message(self, format, *args):
        print("[cert] " + (format % args))


def local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def start_cert_server(host, port):
    httpd = socketserver.TCPServer((host, port), CertOnlyHandler)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    return httpd


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8088, help="Porta HTTPS do app")
    parser.add_argument("--cert-port", type=int, default=8089, help="Porta HTTP só do certificado da CA")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--http", action="store_true", help="Serve o app em HTTP puro (sem PWA instalável)")
    args = parser.parse_args()

    use_https = not args.http
    if use_https and not (SERVER_CERT.exists() and SERVER_KEY.exists()):
        print("Certificado não encontrado. Rode primeiro: python certs/make_cert.py")
        print("Continuando em HTTP (sem instalação de PWA)...")
        use_https = False

    ip = local_ip()
    cert_httpd = None
    if use_https and ROOT_CA.exists():
        cert_httpd = start_cert_server(args.host, args.cert_port)
        print(f"Certificado da CA (instalar no celular 1x): http://{ip}:{args.cert_port}/rootCA.crt")

    with socketserver.TCPServer((args.host, args.port), PwaRequestHandler) as httpd:
        if use_https:
            ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
            ctx.load_cert_chain(certfile=str(SERVER_CERT), keyfile=str(SERVER_KEY))
            httpd.socket = ctx.wrap_socket(httpd.socket, server_side=True)

        scheme = "https" if use_https else "http"
        print(f"Servindo {ROOT} em:")
        print(f"  Local:   {scheme}://localhost:{args.port}")
        print(f"  Na rede: {scheme}://{ip}:{args.port}")
        print("Pressione Ctrl+C para parar.")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nServidor encerrado.")
        finally:
            if cert_httpd:
                cert_httpd.shutdown()


if __name__ == "__main__":
    main()
