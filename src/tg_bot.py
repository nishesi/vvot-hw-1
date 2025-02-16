import json
import logging
import os
import tempfile
import requests
from dotenv import load_dotenv
from telegram import Update
from telegram.ext import CommandHandler, MessageHandler, filters, CallbackContext, Application
import base64
import boto3

load_dotenv()

TELEGRAM_BOT_TOKEN = os.getenv("tg_bot_key")
YANDEX_API_KEY = os.getenv("yandex_api_key")
YANDEX_BUCKET_NAME = os.getenv("bucket_name")
YANDEX_OBJECT_KEY = os.getenv("object_key")
FOLDER_ID = os.getenv("folder_id")
AWS_ACCESS_KEY_ID = os.getenv("aws_access_key_id")
AWS_SECRET_ACCESS_KEY = os.getenv("aws_secret_access_key")
AWS_DEFAULT_REGION = os.getenv("region_name")

URL = "https://llm.api.cloud.yandex.net/foundationModels/v1/completion"

instruction_cache = None

logging.basicConfig(format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
                    level=logging.INFO)
logger = logging.getLogger(__name__)


async def start(update: Update, _: CallbackContext) -> None:
    await update.message.reply_text(
        "Я помогу подготовить ответ на экзаменационный вопрос по дисциплине 'Операционные системы'.\n"
        "Пришлите мне фотографию с вопросом или наберите его текстом."
    )


def get_yandex_object():
    global instruction_cache
    if instruction_cache:
        return instruction_cache

    try:
        session = boto3.session.Session(aws_access_key_id=AWS_ACCESS_KEY_ID,
                                        aws_secret_access_key=AWS_SECRET_ACCESS_KEY,
                                        region_name=AWS_DEFAULT_REGION)
        s3 = session.client(
            service_name='s3',
            endpoint_url='https://storage.yandexcloud.net'
        )
        get_object_response = s3.get_object(Bucket=YANDEX_BUCKET_NAME, Key=YANDEX_OBJECT_KEY)
        instruction_cache = get_object_response['Body'].read().decode('utf-8')
        return instruction_cache

    except Exception as e:
        print(f"Ошибка при загрузке файла из Yandex Object Storage: {e}")
        return None


def generate_answer_from_yandexgpt(instruction, question_text):
    # print(instruction)
    data = {
        "modelUri": f"gpt://{FOLDER_ID}/yandexgpt/rc",
        "completionOptions": {"temperature": 0.5, "maxTokens": 2000},
        "messages": [
            {"role": "system", "text": f"{instruction}"},
            {"role": "user", "text": f"{question_text}"},
        ]
    }

    try:
        response = requests.post(
            URL,
            headers={
                "Accept": "application/json",
                "Authorization": f"Api-Key {YANDEX_API_KEY}",
            },
            json=data,
        )

        if response.status_code == 200:
            response_data = response.json()
            print(response_data)
            return response_data.get('result', {}).get('alternatives', [{}])[0].get('message', {}).get('text',
                                                                                                       'Ответ не найден.')
        else:
            print(f"Ошибка от YandexGPT API: {response.status_code}")
            print(response.text)
            print(response.content)
            return "Я не смог подготовить ответ на экзаменационный вопрос."
    except requests.exceptions.RequestException as e:
        print(f"Ошибка при запросе к YandexGPT API: {e}")
        return "Я не смог подготовить ответ на экзаменационный вопрос."


async def handle_text(update: Update, context: CallbackContext):
    question_text = update.message.text

    instruction = get_yandex_object()

    if instruction:
        answer = generate_answer_from_yandexgpt(instruction, question_text)
        await update.message.reply_text(answer)
    else:
        await update.message.reply_text("Не удалось загрузить инструкцию для YandexGPT API.")


def encode_file(file_path):
    with open(file_path, "rb") as fid:
        file_content = fid.read()
    return base64.b64encode(file_content).decode("utf-8")


async def handle_photo(update: Update, _: CallbackContext) -> None:
    if len(update.message.photo) > 4:
        await update.message.reply_text("Я могу обработать только одну фотографию.")
        return

    photo = update.message.photo[-1]
    file = await photo.get_file()

    with tempfile.NamedTemporaryFile(delete=False, suffix='.jpg') as temp_file:
        await file.download_to_drive(temp_file.name)
        temp_file_path = temp_file.name

    encoded_file = encode_file(temp_file_path)

    data = {
        "mimeType": "JPEG",
        "languageCodes": ["*"],
        "model": "page",
        "content": encoded_file
    }

    ocr_url = "https://ocr.api.cloud.yandex.net/ocr/v1/recognizeText"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Api-Key {YANDEX_API_KEY}",
        "x-folder-id": FOLDER_ID,
        "x-data-logging-enabled": "true"
    }

    response = requests.post(ocr_url, headers=headers, data=json.dumps(data))

    if response.status_code == 200:
        result = response.json().get('result', {})
        text_annotation = result.get('textAnnotation', {})
        if text_annotation:
            ocr_text = text_annotation.get('fullText', '')
            if ocr_text:
                instruction = get_yandex_object()
                answer = generate_answer_from_yandexgpt(instruction, ocr_text)
                await update.message.reply_text(answer)
            else:
                await update.message.reply_text("Не удалось распознать текст на фотографии.")
        else:
            await update.message.reply_text("Не удалось распознать текст на фотографии.")
    else:
        print(f"Ошибка от YandexGPT API: {response.status_code}")
        print(response.text)
        print(response.content)
        await update.message.reply_text("Я не могу обработать эту фотографию.")


async def handle_other(update: Update, _: CallbackContext) -> None:
    await update.message.reply_text("Я могу обработать только текстовое сообщение или фотографию.")


async def handler(event, context):
    application = Application.builder().token(TELEGRAM_BOT_TOKEN).build()

    if not application._initialized:
        await application.initialize()

    if not application.handlers:
        application.add_handler(CommandHandler("start", start))
        application.add_handler(CommandHandler("help", start))
        application.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_text))
        application.add_handler(MessageHandler(filters.PHOTO, handle_photo))
        application.add_handler(MessageHandler(filters.ALL & ~filters.COMMAND & ~filters.PHOTO, handle_other))

    try:
        body = json.loads(event['body'])
        print(body)
        update = Update.de_json(body, application.bot)
        await application.process_update(update)

        print("return")
        return {
            "statusCode": 200,
            "body": json.dumps({"status": "ok"}),
        }
    except Exception as e:
        logger.error(f"Ошибка при обработке запроса: {e}")
        return {
            "statusCode": 500,
            "body": json.dumps({"error": "Ошибка обработки запроса"}),
        }