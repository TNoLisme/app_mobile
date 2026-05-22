import os

def force_replace(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    replacements = {
        'Ä Ã£': 'Đã',
        'ChÆ°a': 'Chưa',
        'táº¡m tÃ­nh': 'tạm tính',
        'Ä Ãºng rá»“i': 'Đúng rồi',
        'khuÃ´n máº·t': 'khuôn mặt',
        'HÃ£y ghÃ©p': 'Hãy ghép',
        'buá»“n bÃ£': 'buồn bã',
        'tá»©c giáº­n': 'tức giận',
        'sá»£ hÃ£i': 'sợ hãi',
        'ngáº¡c nhiÃªn': 'ngạc nhiên',
        'nÃ o phÃ¹ há»£p': 'nào phù hợp',
        'Ä‘Æ°á»£c táº·ng': 'được tặng',
        'giáº­t Ä‘á»“ chÆ¡i khá» i': 'giật đồ chơi khỏi',
        'xuáº¥t hiá»‡n': 'xuất hiện',
        'áº©n giáº¥u': 'ẩn giấu',
        'PhÃ¡ Ã¡n': 'Phá án',
        'bÃ¡m cháº·t': 'bám chặt',
        'máº¹': 'mẹ',
        'tháº¥y': 'thấy',
        'chÃ³ lá»›n': 'chó lớn',
        'Ä Ã¢y lÃ ': 'Đây là',
        'gÃ¬': 'gì',
        'â†  ThoÃ¡t': '← Thoát',
        'Káº¿t thÃºc': 'Kết thúc',
        'Đáp án lÃ ': 'Đáp án là'
    }

    new_content = content
    for old, new in replacements.items():
        new_content = new_content.replace(old, new)
        
    if new_content != content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print('Replaced in ' + file_path)

force_replace(r'D:\school\code\emo\app_mobile\android\app\src\main\java\com\example\appmobile\ui\pages\game\GameClick3Page.kt')
force_replace(r'D:\school\code\emo\app_mobile\android\app\src\main\java\com\example\appmobile\ui\pages\game\GameClick4Page.kt')
force_replace(r'D:\school\code\emo\app_mobile\android\app\src\main\java\com\example\appmobile\ui\pages\game\CvTrainingGamePage.kt')
force_replace(r'D:\school\code\emo\app_mobile\android\app\src\main\java\com\example\appmobile\ui\pages\game\GameClick2Page.kt')
force_replace(r'D:\school\code\emo\app_mobile\android\app\src\main\java\com\example\appmobile\ui\pages\game\RecognizeEmotionPage.kt')
