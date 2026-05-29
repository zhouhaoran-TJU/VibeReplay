#!/usr/bin/env python3
import sys

import qrcode


def main():
    if len(sys.argv) != 3:
        print("usage: make_qr.py <text> <output.png>", file=sys.stderr)
        return 2

    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=12,
        border=4,
    )
    qr.add_data(sys.argv[1])
    qr.make(fit=True)
    image = qr.make_image(fill_color="black", back_color="white").convert("RGB")
    image.save(sys.argv[2])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
