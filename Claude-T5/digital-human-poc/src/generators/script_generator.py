"""演讲稿生成器 - 基于 LLM 将 PPT 内容转为自然演讲稿"""

from openai import OpenAI
from src.config import LLM_API_KEY, LLM_BASE_URL, LLM_MODEL, LLM_TEMPERATURE

SYSTEM_PROMPT_ZH = """你是一位专业的演讲稿撰写专家。你的任务是将 PPT 的内容转化为自然、流畅的口语化演讲稿。

要求：
1. 语言自然，像真人演讲一样，避免书面化表述
2. 每页内容对应一段演讲，用 [PAGE:N] 标记页码
3. 在适当位置加入过渡语（"接下来"、"这里值得注意的是" 等）
4. 对关键数据和重点内容加强调语气标记：**粗体**
5. 用 [PAUSE] 标记需要停顿的位置
6. 用 [EMPHASIS] 标记需要重读的内容
7. 开头要有吸引人的开场白，结尾要有总结
8. 控制每页演讲时长约 30-60 秒

输出格式示例：
---
[PAGE:0]
大家好，今天我来为大家汇报......

[PAUSE]

[PAGE:1]
接下来我们看一下......

[EMPHASIS] 这是本页的重点 **关键数据**

[PAUSE]
---
"""

SYSTEM_PROMPT_EN = """You are a professional speech writing expert. Your task is to transform PPT content into a natural, fluent oral presentation script.

Requirements:
1. Use natural, conversational language — avoid overly formal or written-style expressions
2. Each slide corresponds to one speech segment, marked with [PAGE:N]
3. Add natural transitions between sections ("Now let's look at...", "This is worth noting...")
4. Emphasize key data and important points with **bold** markers
5. Use [PAUSE] to mark natural pauses
6. Use [EMPHASIS] to mark content that should be stressed
7. Include an engaging opening and a summary at the end
8. Aim for about 30-60 seconds of speech per slide

Output format example:
---
[PAGE:0]
Hello everyone, today I'd like to present......

[PAUSE]

[PAGE:1]
Now let's take a look at......

[EMPHASIS] This is the key point of this slide **important data**

[PAUSE]
---
"""


def generate_script(
    slides_markdown: str,
    style: str = "formal",
    language: str = "zh",
    duration_minutes: int | None = None,
) -> str:
    """
    根据PPT内容生成演讲稿

    Args:
        slides_markdown: PPT的Markdown格式内容
        style: 演讲风格 (formal-正式汇报, casual-轻松讲解, training-培训教学)
        language: 语言 (zh-中文, en-英文)
        duration_minutes: 预期时长（分钟），None则自动控制
    """
    if not LLM_API_KEY:
        raise ValueError("请设置 LLM_API_KEY 环境变量（在 .env 文件中）")

    client = OpenAI(api_key=LLM_API_KEY, base_url=LLM_BASE_URL)

    style_map_zh = {
        "formal": "正式商务汇报风格，语气专业严谨",
        "casual": "轻松自然的讲解风格，像和朋友聊天",
        "training": "培训教学风格，条理清晰，适合学习",
    }
    style_map_en = {
        "formal": "a formal business presentation style, professional and precise",
        "casual": "a relaxed, conversational style, like chatting with friends",
        "training": "a training and teaching style, well-structured for learning",
    }

    if language == "en":
        system_prompt = SYSTEM_PROMPT_EN
        style_desc = style_map_en.get(style, style_map_en["formal"])
        user_prompt = f"Here is the full content of a presentation. Please transform it into {style_desc} speech script:\n\n{slides_markdown}"
    else:
        system_prompt = SYSTEM_PROMPT_ZH
        style_desc = style_map_zh.get(style, style_map_zh["formal"])
        user_prompt = f"以下是 PPT 的全部内容，请将其转化为{style_desc}的演讲稿：\n\n{slides_markdown}"
    if duration_minutes:
        if language == "en":
            user_prompt += f"\n\nPlease aim for a total presentation duration of about {duration_minutes} minutes."
        else:
            user_prompt += f"\n\n请控制总演讲时长约 {duration_minutes} 分钟。"

    response = client.chat.completions.create(
        model=LLM_MODEL,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        temperature=LLM_TEMPERATURE,
        max_tokens=4096,
    )

    return response.choices[0].message.content


def parse_script_pages(script: str) -> dict[int, str]:
    """将生成的演讲稿按页拆分，返回 {页码: 演讲文本}"""
    pages = {}
    current_page = 0
    current_text = []

    for line in script.split("\n"):
        stripped = line.strip()
        if stripped.startswith("[PAGE:"):
            # 保存上一页
            if current_text:
                pages[current_page] = "\n".join(current_text).strip()
            # 解析页码
            try:
                current_page = int(stripped.replace("[PAGE:", "").replace("]", ""))
            except ValueError:
                current_page += 1
            current_text = []
        else:
            current_text.append(line)

    # 最后一页
    if current_text:
        pages[current_page] = "\n".join(current_text).strip()

    return pages


if __name__ == "__main__":
    import sys
    from src.parsers.ppt_parser import parse_ppt, slides_to_markdown

    if len(sys.argv) < 2:
        print("用法: python script_generator.py <ppt文件路径> [style=formal]")
        sys.exit(1)

    slides = parse_ppt(sys.argv[1])
    md = slides_to_markdown(slides)
    style = sys.argv[2] if len(sys.argv) > 2 else "formal"

    print("正在生成演讲稿...")
    result = generate_script(md, style=style)
    print(result)
