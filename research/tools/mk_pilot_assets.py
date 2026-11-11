# tools/mk_pilot_assets.py — 生成 Pilot 布景占位图（虚构测试素材）
from PIL import Image, ImageDraw, ImageFont
import os

os.makedirs('benchmark/media/pilot', exist_ok=True)

def font(size):
    try:
        return ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', size)
    except Exception:
        return None

def mk(name, lines, w=540, h=360, bg=(245, 244, 240)):
    img = Image.new('RGB', (w, h), bg)
    d = ImageDraw.Draw(img)
    y = 40
    for t, fs in lines:
        d.text((30, y), t, fill=(60, 60, 60), font=font(fs))
        y += fs + 18
    img.save('benchmark/media/pilot/' + name)
    print('ok', name)

mk('L1_t07_phone.png', [('【测试布景·虚构商品】', 22), ('星耀 Pro Max', 30), ('12GB+256GB', 24), ('￥5999', 40), ('12期免息 | 赠原装充电器', 20)])
mk('L2_t07_mall1.png', [('【测试布景·虚构照片】', 22), ('周三的万象城', 30), ('中庭排队中', 24)])
mk('L2_t07_mall2.png', [('【测试布景·虚构照片】', 22), ('冰淇淋店门口', 30), ('排队 40 分钟', 24)])
mk('L2_t25_shoe.png', [('【测试布景·虚构商品】', 22), ('羽毛球鞋 白色', 26), ('￥469', 34), ('防滑拼色底', 20)])
print('done')
