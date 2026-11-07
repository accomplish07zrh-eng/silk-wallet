"""
重新生成 res/font 下的两个字体子集。
用法：python tools/subset_fonts.py <项目根目录>
源字体放在 D:/Android/work/fonts/：
  playfair-italic.ttf   —— google/fonts ofl/playfairdisplay/PlayfairDisplay-Italic[wght].ttf
  NotoSerifSC-Bold.otf  —— notofonts/noto-cjk Serif/SubsetOTF/SC/NotoSerifSC-Bold.otf
新增中文文案后重跑本脚本，宋体子集才会包含新字。
"""
import sys, os, re, glob
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools import subset

BS = chr(92)
root = BS + BS + '?' + BS + os.path.abspath(sys.argv[1]).replace('/', BS)
out = os.path.join(root, 'app', 'src', 'main', 'res', 'font')
os.makedirs(out, exist_ok=True)

# 1) Playfair Display Italic 固化为 700，只留拉丁字符
f = TTFont('D:/Android/work/fonts/playfair-italic.ttf')
f = instancer.instantiateVariableFont(f, {"wght": 700})
f.save('D:/Android/work/fonts/pf700.ttf')
opts = subset.Options()
opts.layout_features = ['*']
sub = subset.Subsetter(opts)
sub.populate(text=''.join(chr(c) for c in range(0x20, 0x7f)))
ft = TTFont('D:/Android/work/fonts/pf700.ttf')
sub.subset(ft)
ft.save(os.path.join(out, 'playfair_bold_italic.ttf'))

# 2) 思源宋体：只保留源码字符串字面量里出现过的汉字与标点
chars = set()
lit_re = re.compile('"([^"\n]*)"')
src = os.path.join(root, 'app', 'src', 'main', 'java')
for dirpath, _, files in os.walk(src):
    for name in files:
        if not name.endswith('.kt'):
            continue
        for lit in lit_re.findall(open(os.path.join(dirpath, name), encoding='utf-8').read()):
            chars.update(ch for ch in lit if ord(ch) > 0x2e7f)
chars.update('0123456789 ·「」：，。、？！（）')
chars.update(chr(c) for c in range(0x20, 0x7f))
print('chars', len(chars))
opts = subset.Options()
opts.layout_features = ['*']
sub = subset.Subsetter(opts)
sub.populate(text=''.join(chars))
ft = TTFont('D:/Android/work/fonts/NotoSerifSC-Bold.otf')
sub.subset(ft)
ft.save(os.path.join(out, 'noto_serif_sc_bold.otf'))
for n in os.listdir(out):
    print(n, os.path.getsize(os.path.join(out, n)))
