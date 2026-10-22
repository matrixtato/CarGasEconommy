"""Gera (ou reaproveita) uma CA local e emite um certificado de servidor para
a PWA rodar em HTTPS na rede local (necessário para Service Worker e
instalação do app).

Uso:
    python make_cert.py [IP_ou_host_adicional ...]
    python make_cert.py --force-new-ca [...]   # gera uma CA nova (exige reinstalar no celular)

Sempre inclui localhost/127.0.0.1 e TODOS os IPv4 das interfaces de rede
deste PC (Ethernet, Wi-Fi etc.), além de qualquer host extra passado por
argumento. Rodar de novo (ex.: IP mudou) reaproveita a CA existente — o
celular que já confia nela não precisa reinstalar nada, só o certificado do
servidor é reemitido.

Gera:
    rootCA.crt   -> instalar no celular (autoridade confiável), 1x só
    rootCA.key   -> chave privada da CA (NÃO distribuir; fica só aqui)
    server.key   -> chave privada do servidor (NÃO distribuir)
    server.pem   -> certificado do servidor assinado pela rootCA
"""
import datetime
import ipaddress
import socket
import sys
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

OUT = Path(__file__).resolve().parent
OUT.mkdir(exist_ok=True)

CA_CERT_FILE = OUT / "rootCA.crt"
CA_KEY_FILE = OUT / "rootCA.key"


def detect_lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def detect_all_ipv4():
    ips = {detect_lan_ip()}
    try:
        _, _, addrs = socket.gethostbyname_ex(socket.gethostname())
        ips.update(addrs)
    except OSError:
        pass
    return {ip for ip in ips if not ip.startswith("169.254.")}


def build_san(extra_hosts):
    hosts = {"localhost", "127.0.0.1", *detect_all_ipv4(), *extra_hosts}
    names = []
    for h in hosts:
        try:
            names.append(x509.IPAddress(ipaddress.ip_address(h)))
        except ValueError:
            names.append(x509.DNSName(h))
    return names, hosts


def make_key():
    return rsa.generate_private_key(public_exponent=65537, key_size=2048)


def load_or_create_ca(force_new):
    if not force_new and CA_CERT_FILE.exists() and CA_KEY_FILE.exists():
        ca_key = serialization.load_pem_private_key(CA_KEY_FILE.read_bytes(), password=None)
        ca_cert = x509.load_pem_x509_certificate(CA_CERT_FILE.read_bytes())
        print("Reaproveitando CA existente (celular não precisa reinstalar o certificado).")
        return ca_key, ca_cert, ca_cert.subject

    now = datetime.datetime.now(datetime.timezone.utc)
    ca_key = make_key()
    ca_name = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, "CarGasEconommy Local CA"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "CarGasEconommy PWA"),
    ])
    ca_cert = (
        x509.CertificateBuilder()
        .subject_name(ca_name)
        .issuer_name(ca_name)
        .public_key(ca_key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - datetime.timedelta(days=1))
        .not_valid_after(now + datetime.timedelta(days=3650))
        .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
        .add_extension(
            x509.KeyUsage(
                digital_signature=False, content_commitment=False, key_encipherment=False,
                data_encipherment=False, key_agreement=False, key_cert_sign=True,
                crl_sign=True, encipher_only=False, decipher_only=False,
            ),
            critical=True,
        )
        .add_extension(x509.SubjectKeyIdentifier.from_public_key(ca_key.public_key()), critical=False)
        .sign(ca_key, hashes.SHA256())
    )

    CA_KEY_FILE.write_bytes(
        ca_key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.TraditionalOpenSSL,
            serialization.NoEncryption(),
        )
    )
    CA_CERT_FILE.write_bytes(ca_cert.public_bytes(serialization.Encoding.PEM))
    print("Nova CA gerada — instale (ou reinstale) rootCA.crt no celular.")
    return ca_key, ca_cert, ca_name


def main():
    force_new_ca = "--force-new-ca" in sys.argv
    extra_hosts = [a for a in sys.argv[1:] if not a.startswith("--")]
    san_names, hosts = build_san(extra_hosts)

    now = datetime.datetime.now(datetime.timezone.utc)

    ca_key, ca_cert, ca_name = load_or_create_ca(force_new_ca)

    # ---- Certificado do servidor ----
    server_key = make_key()
    server_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, detect_lan_ip())])
    server_cert = (
        x509.CertificateBuilder()
        .subject_name(server_name)
        .issuer_name(ca_name)
        .public_key(server_key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - datetime.timedelta(days=1))
        .not_valid_after(now + datetime.timedelta(days=825))
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .add_extension(x509.SubjectAlternativeName(san_names), critical=False)
        .add_extension(
            x509.KeyUsage(
                digital_signature=True, content_commitment=False, key_encipherment=True,
                data_encipherment=False, key_agreement=False, key_cert_sign=False,
                crl_sign=False, encipher_only=False, decipher_only=False,
            ),
            critical=True,
        )
        .add_extension(
            x509.ExtendedKeyUsage([x509.oid.ExtendedKeyUsageOID.SERVER_AUTH]), critical=False
        )
        .sign(ca_key, hashes.SHA256())
    )

    (OUT / "server.pem").write_bytes(server_cert.public_bytes(serialization.Encoding.PEM))
    (OUT / "server.key").write_bytes(
        server_key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.TraditionalOpenSSL,
            serialization.NoEncryption(),
        )
    )

    print("Gerado em:", OUT)
    print("Hosts/IPs no certificado:", ", ".join(sorted(hosts)))
    print("Instale rootCA.crt no celular e reinicie o server.py")


if __name__ == "__main__":
    main()
