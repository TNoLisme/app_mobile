import os
import glob

mapping = {
    "CÃ¢u": "Câu",
    "tiáº¿p theo": "tiếp theo",
    "TÃ¬nh huá»‘ng": "Tình huống",
    "Chiáº¿c há»™p cáº£m xÃºc": "Chiếc hộp cảm xúc",
    "Cáº£m xÃºc Ä‘Ãºng chá»—": "Cảm xúc đúng chỗ",
    "Cáº£m xÃºc": "Cảm xúc",
    "cáº£m xÃºc": "cảm xúc",
    "Tráº£ lá»\x9di": "Trả lời",
    "Tráº£ lá»\x8di": "Trả lời",
    "Tráº£ lá»\x8fi": "Trả lời",
    "Tráº£ lá» i": "Trả lời",
    "Ä\x90iá»ƒm": "Điểm",
    "Ä iá»ƒm": "Điểm",
    "Quay láº¡i": "Quay lại",
    "Ä‘Ãºng": "đúng",
    "chá»—": "chỗ",
    "BÃ© hÃ£y": "Bé hãy",
    "nhÃ¬n hÃ¬nh vÃ ": "nhìn hình và ",
    "chá»\xa0n": "chọn",
    "chá» n": "chọn",
    "nháº¥t nhÃ©": "nhất nhé",
    "Ä\x90Ã¡p Ã¡n": "Đáp án",
    "Ä\x90Ã£ hiá»ƒu": "Đã hiểu",
    "Ä Ã£ hiá»ƒu": "Đã hiểu",
    "Vui váº»": "Vui vẻ",
    "Buá»“n bÃ£": "Buồn bã",
    "Tá»©c giáº\xadn": "Tức giận",
    "Tá»©c giáº\xafn": "Tức giận",
    "Tá»©c giáºn": "Tức giận",
    "Sá»£ hÃ£i": "Sợ hãi",
    "Ngáº¡c nhiÃªn": "Ngạc nhiên",
    "GhÃª tá»Ÿm": "Ghê tởm",
    "KhÃ´ng cÃ³": "Không có",
    "Gá»£i Ã½": "Gợi ý",
    "Ã”n táº\xadp": "Ôn tập",
    "Ã”n táºp": "Ôn tập",
    "ThÃ¡m tá»": "Thám tử",
    "XÆ°á»Ÿng láº¯p ghÃ©p": "Xưởng lắp ghép",
    "Ä\x90Ãºng rá»“i!": "Đúng rồi!",
    "Tiáº¿c quÃ¡": "Tiếc quá",
    "â† ": "←",
    "KhuÃ´n máº·t": "Khuôn mặt",
    "Ä ang ghÃ©p": "đang ghép",
    "Ä ang lÆ°u...": "Đang lưu...",
    "HoÃ\xa0n thÃ\xa0nh": "Hoàn thành",
    "HoÃ n thÃ nh": "Hoàn thành",
    "ChÆ°a chá» n": "Chưa chọn",
    "Chá» n láº¡i": "Chọn lại",
    "Kiá»ƒm tra": "Kiểm tra",
    "YÃªu cáº§u": "Yêu cầu",
    "LÃ´ng mÃ\xa0y": "Lông mày",
    "LÃ´ng mÃ y": "Lông mày",
    "Máº¯t": "Mắt",
    "Miá»‡ng": "Miệng",
    "GhÃ©p khuÃ´n máº·t": "Ghép khuôn mặt",
    "Chá» n cÃ¹ng má»™t cáº£m xÃºc cho cáº£ 3 pháº§n Ä‘á»ƒ táº¡o khuÃ´n máº·t Ä‘Ãºng.": "Chọn cùng một cảm xúc cho cả 3 phần để tạo khuôn mặt đúng.",
    "BÃ© cáº§n kÃ©o Ä‘á»§ tÃ¬nh huá»‘ng vÃ\xa0 cáº£m xÃºc tÆ°Æ¡ng á»©ng.": "Bé cần kéo đủ tình huống và cảm xúc tương ứng."
}

# Apply to all Game pages
for file in glob.glob('d:/school/code/emo/app_mobile/android/app/src/main/java/com/example/appmobile/ui/pages/**/*.kt', recursive=True):
    with open(file, 'r', encoding='utf-8') as f:
        text = f.read()
    
    orig = text
    for k, v in mapping.items():
        text = text.replace(k, v)
    
    if orig != text:
        with open(file, 'w', encoding='utf-8') as f:
            f.write(text)
        print(f'Fixed {os.path.basename(file)}')
