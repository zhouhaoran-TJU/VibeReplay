#!/usr/bin/env python3
import sys
from PIL import Image


GALOIS_FIELD = 0x11D
VERSION = 5
SIZE = 37
ECC_CODEWORDS = 26
DATA_CODEWORDS = 108


def gf_mul(x, y):
    result = 0
    while y:
        if y & 1:
            result ^= x
        x <<= 1
        if x & 0x100:
            x ^= GALOIS_FIELD
        y >>= 1
    return result


def gf_pow(x, power):
    result = 1
    for _ in range(power):
        result = gf_mul(result, x)
    return result


def generator_poly(degree):
    poly = [1]
    for i in range(degree):
        next_poly = [0] * (len(poly) + 1)
        root = gf_pow(2, i)
        for j, value in enumerate(poly):
            next_poly[j] ^= gf_mul(value, root)
            next_poly[j + 1] ^= value
        poly = next_poly
    return poly


def reed_solomon(data, degree):
    gen = generator_poly(degree)
    result = data[:] + [0] * degree
    for i, value in enumerate(data):
        factor = result[i]
        if factor == 0:
            continue
        for j, gen_value in enumerate(gen):
            result[i + j] ^= gf_mul(gen_value, factor)
    return result[-degree:]


def append_bits(bits, value, length):
    for i in range(length - 1, -1, -1):
        bits.append((value >> i) & 1)


def encode_bytes(text):
    payload = text.encode("utf-8")
    if len(payload) > 106:
        raise ValueError("Version 5-L byte QR can hold at most 106 bytes")
    bits = []
    append_bits(bits, 0b0100, 4)
    append_bits(bits, len(payload), 8)
    for byte in payload:
        append_bits(bits, byte, 8)
    terminator = min(4, DATA_CODEWORDS * 8 - len(bits))
    append_bits(bits, 0, terminator)
    while len(bits) % 8:
        bits.append(0)
    data = []
    for i in range(0, len(bits), 8):
        value = 0
        for bit in bits[i:i + 8]:
            value = (value << 1) | bit
        data.append(value)
    pads = [0xEC, 0x11]
    pad_index = 0
    while len(data) < DATA_CODEWORDS:
        data.append(pads[pad_index % 2])
        pad_index += 1
    return data


def empty_matrix():
    modules = [[False] * SIZE for _ in range(SIZE)]
    reserved = [[False] * SIZE for _ in range(SIZE)]
    return modules, reserved


def set_module(modules, reserved, x, y, value, reserve=True):
    modules[y][x] = value
    if reserve:
        reserved[y][x] = True


def draw_finder(modules, reserved, x, y):
    for yy in range(y - 1, y + 8):
        for xx in range(x - 1, x + 8):
            if 0 <= xx < SIZE and 0 <= yy < SIZE:
                reserved[yy][xx] = True
                if x <= xx <= x + 6 and y <= yy <= y + 6:
                    border = xx in (x, x + 6) or yy in (y, y + 6)
                    center = x + 2 <= xx <= x + 4 and y + 2 <= yy <= y + 4
                    modules[yy][xx] = border or center


def draw_patterns(modules, reserved):
    draw_finder(modules, reserved, 0, 0)
    draw_finder(modules, reserved, SIZE - 7, 0)
    draw_finder(modules, reserved, 0, SIZE - 7)
    for i in range(8, SIZE - 8):
        set_module(modules, reserved, i, 6, i % 2 == 0)
        set_module(modules, reserved, 6, i, i % 2 == 0)
    for y in range(28, 33):
        for x in range(28, 33):
            set_module(modules, reserved, x, y, x in (28, 32) or y in (28, 32) or (x == 30 and y == 30))
    set_module(modules, reserved, 8, SIZE - 8, True)
    for i in range(9):
        if i != 6:
            reserved[8][i] = True
            reserved[i][8] = True
            reserved[8][SIZE - 1 - i] = True
            reserved[SIZE - 1 - i][8] = True
    for i in range(8):
        reserved[SIZE - 8 + i][8] = True
        reserved[8][SIZE - 8 + i] = True


def place_data(modules, reserved, bits):
    index = 0
    upward = True
    x = SIZE - 1
    while x > 0:
        if x == 6:
            x -= 1
        rows = range(SIZE - 1, -1, -1) if upward else range(SIZE)
        for y in rows:
            for dx in (0, 1):
                xx = x - dx
                if reserved[y][xx]:
                    continue
                bit = bits[index] if index < len(bits) else 0
                mask = (xx + y) % 2 == 0
                modules[y][xx] = bool(bit) ^ mask
                index += 1
        upward = not upward
        x -= 2


def format_bits():
    value = (0b01 << 3) | 0b000
    bits = value << 10
    generator = 0b10100110111
    for i in range(14, 9, -1):
        if (bits >> i) & 1:
            bits ^= generator << (i - 10)
    return ((value << 10) | bits) ^ 0b101010000010010


def draw_format(modules, reserved):
    bits = format_bits()
    first = [(8, 0), (8, 1), (8, 2), (8, 3), (8, 4), (8, 5), (8, 7), (8, 8),
             (7, 8), (5, 8), (4, 8), (3, 8), (2, 8), (1, 8), (0, 8)]
    second = [(SIZE - 1, 8), (SIZE - 2, 8), (SIZE - 3, 8), (SIZE - 4, 8), (SIZE - 5, 8),
              (SIZE - 6, 8), (SIZE - 7, 8), (8, SIZE - 8), (8, SIZE - 7), (8, SIZE - 6),
              (8, SIZE - 5), (8, SIZE - 4), (8, SIZE - 3), (8, SIZE - 2), (8, SIZE - 1)]
    for i in range(15):
        bit = bool((bits >> i) & 1)
        x, y = first[i]
        set_module(modules, reserved, x, y, bit)
        x, y = second[i]
        set_module(modules, reserved, x, y, bit)


def make_qr(text):
    data = encode_bytes(text)
    codewords = data + reed_solomon(data, ECC_CODEWORDS)
    bits = []
    for byte in codewords:
        append_bits(bits, byte, 8)
    modules, reserved = empty_matrix()
    draw_patterns(modules, reserved)
    place_data(modules, reserved, bits)
    draw_format(modules, reserved)
    return modules


def save_png(modules, output):
    scale = 12
    quiet = 4
    image_size = (SIZE + quiet * 2) * scale
    image = Image.new("RGB", (image_size, image_size), "white")
    pixels = image.load()
    for y, row in enumerate(modules):
        for x, value in enumerate(row):
            if not value:
                continue
            for yy in range((y + quiet) * scale, (y + quiet + 1) * scale):
                for xx in range((x + quiet) * scale, (x + quiet + 1) * scale):
                    pixels[xx, yy] = (0, 0, 0)
    image.save(output)


def main():
    if len(sys.argv) != 3:
        print("usage: make_qr.py <text> <output.png>", file=sys.stderr)
        return 2
    save_png(make_qr(sys.argv[1]), sys.argv[2])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
