1. LLM 接入信息
- 接入模型：Doubao-1-5-lite-32
- 推理接入点名称:doubao-1-5-lite-32k_Hackathon
- 推理接入点ID：ep-20251220162528-gxhqh
- API Key名称：doubao-1-5-lite-32k_Hackathon
- API Key：05c0940f-f26a-429b-baa0-12b13d2df3d9
- 整体限流：150 RPM；25000 TPM
1.1 接入示例
完整可以参考官方示例：volcengine.com/docs/82379/1399008
1.1.1 REST API调用示例
curl https://ark.cn-beijing.volces.com/api/v3/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer 05c0940f-f26a-429b-baa0-12b13d2df3d9" \
  -d '{
    "model": "ep-20251220162528-gxhqh",
    "messages": [
      {"role": "system","content": "你是人工智能助手."},
      {"role": "user","content": "你好"}
    ]
  }'

1.1.2 OpenAI SDK调用示例
1.1.2.1 Python
请按如下命令安装环境
pip install --upgrade "openai>=1.0"
请参考如下示例代码进行调用
import os
from openai import OpenAI

# 请确保您已将 API Key 存储在环境变量 ARK_API_KEY 中
# 初始化Openai客户端，从环境变量中读取您的API Key
client = OpenAI(
    # 此为默认路径，您可根据业务所在地域进行配置
    base_url="https://ark.cn-beijing.volces.com/api/v3",
    # 从环境变量中获取您的 API Key
    api_key=os.environ.get("ARK_API_KEY"),
)

# Non-streaming:
print("----- standard request -----")
completion = client.chat.completions.create(
    # 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
    model="ep-20251220162528-gxhqh",
    messages=[
        {"role": "system", "content": "你是人工智能助手"},
        {"role": "user", "content": "你好"},
    ],
)
print(completion.choices[0].message.content)

# Streaming:
print("----- streaming request -----")
stream = client.chat.completions.create(
    # 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
    model="ep-20251220162528-gxhqh",
    messages=[
        {"role": "system", "content": "你是人工智能助手"},
        {"role": "user", "content": "你好"},
    ],
    # 响应内容是否流式返回
    stream=True,
)
for chunk in stream:
    if not chunk.choices:
        continue
    print(chunk.choices[0].delta.content, end="")
print()

1.1.2.2 Golang
package main

import (
    "context"
    "fmt"
    "io"
    "os"

    ark "github.com/sashabaranov/go-openai"
)

func main() {
    config := ark.DefaultConfig(os.Getenv("ARK_API_KEY"))
    config.BaseURL = "https://ark.cn-beijing.volces.com/api/v3"
    client := ark.NewClientWithConfig(config)

    fmt.Println("----- standard request -----")
    resp, err := client.CreateChatCompletion(
       context.Background(),
       ark.ChatCompletionRequest{
          Model: "ep-20251220162528-gxhqh",
          Messages: []ark.ChatCompletionMessage{
             {
                Role:    ark.ChatMessageRoleSystem,
                Content: "你是人工智能助手",
             },
             {
                Role:    ark.ChatMessageRoleUser,
                Content: "你好",
             },
          },
       },
    )
    if err != nil {
       fmt.Printf("ChatCompletion error: %v\n", err)
       return
    }
    fmt.Println(resp.Choices[0].Message.Content)

    fmt.Println("----- streaming request -----")
    stream, err := client.CreateChatCompletionStream(
       context.Background(),
       ark.ChatCompletionRequest{
          Model: "ep-20251220162528-gxhqh",
          Messages: []ark.ChatCompletionMessage{
             {
                Role:    ark.ChatMessageRoleSystem,
                Content: "你是人工智能助手",
             },
             {
                Role:    ark.ChatMessageRoleUser,
                Content: "你好",
             },
          },
       },
    )
    if err != nil {
       fmt.Printf("stream chat error: %v\n", err)
       return
    }
    defer stream.Close()

    for {
       recv, err := stream.Recv()
       if err == io.EOF {
          return
       }
       if err != nil {
          fmt.Printf("Stream chat error: %v\n", err)
          return
       }

       if len(recv.Choices) > 0 {
          fmt.Print(recv.Choices[0].Delta.Content)
       }
    }
}
1.1.2.3 Node.js
请按如下命令安装环境
npm install openai
请参考如下示例代码进行调用
import OpenAI from 'openai';

const openai = new OpenAI({
  apiKey: process.env['ARK_API_KEY'],
  baseURL: 'https://ark.cn-beijing.volces.com/api/v3',
});

async function main() {
  // Non-streaming:
  console.log('----- standard request -----')
  const completion = await openai.chat.completions.create({
    messages: [
      { role: 'system', content: '你是人工智能助手' },
      { role: 'user', content: '你好' },
    ],
    model: 'ep-20251220162528-gxhqh',
  });
  console.log(completion.choices[0]?.message?.content);

  // Streaming:
  console.log('----- streaming request -----')
  const stream = await openai.chat.completions.create({
    messages: [
      { role: 'system', content: '你是人工智能助手' },
      { role: 'user', content: '你好' },
    ],
    model: 'ep-20251220162528-gxhqh',
    stream: true,
  });
  for await (const part of stream) {
    process.stdout.write(part.choices[0]?.delta?.content || '');
  }
  process.stdout.write('\n');
}

main();

1.1.3 火山引擎 SDK 调用示例
1.1.3.1 Python
请按如下命令安装环境
pip install --upgrade "volcengine-python-sdk[ark]"
请参考如下示例代码进行调用
import os
from volcenginesdkarkruntime import Ark

# 请确保您已将 API Key 存储在环境变量 ARK_API_KEY 中
# 初始化Ark客户端，从环境变量中读取您的API Key
client = Ark(
    # 此为默认路径，您可根据业务所在地域进行配置
    base_url="https://ark.cn-beijing.volces.com/api/v3",
    # 从环境变量中获取您的 API Key。此为默认方式，您可根据需要进行修改
    api_key=os.environ.get("ARK_API_KEY"),
)

# Non-streaming:
print("----- standard request -----")
completion = client.chat.completions.create(
   # 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
    model="ep-20251220162528-gxhqh",
    messages=[
        {"role": "system", "content": "你是人工智能助手."},
        {"role": "user", "content": "你好"},
    ],
    # 免费开启推理会话应用层加密，访问 https://www.volcengine.com/docs/82379/1389905 了解更多
    extra_headers={'x-is-encrypted': 'true'},
)
print(completion.choices[0].message.content)

# Streaming:
print("----- streaming request -----")
stream = client.chat.completions.create(
    model="ep-20251220162528-gxhqh",
    messages=[
        {"role": "system", "content": "你是人工智能助手."},
        {"role": "user", "content": "你好"},
    ],
    # 免费开启推理会话应用层加密，访问 https://www.volcengine.com/docs/82379/1389905 了解更多
    extra_headers={'x-is-encrypted': 'true'},
    # 响应内容是否流式返回
    stream=True,
)
for chunk in stream:
    if not chunk.choices:
        continue
    print(chunk.choices[0].delta.content, end="")
print()

1.1.3.2 Golang
请按如下命令安装环境
go get -u github.com/volcengine/volcengine-go-sdk
请参考如下示例代码进行调用
package main

import (
    "context"
    "fmt"
    "io"
    "os"

    "github.com/volcengine/volcengine-go-sdk/service/arkruntime"
    "github.com/volcengine/volcengine-go-sdk/service/arkruntime/model"
    "github.com/volcengine/volcengine-go-sdk/volcengine"
)

func main() {
    // 请确保您已将 API Key 存储在环境变量 ARK_API_KEY 中
    // 初始化Ark客户端，从环境变量中读取您的API Key
    client := arkruntime.NewClientWithApiKey(
       // 从环境变量中获取您的 API Key。此为默认方式，您可根据需要进行修改
       os.Getenv("ARK_API_KEY"),
       // 此为默认路径，您可根据业务所在地域进行配置
       arkruntime.WithBaseUrl("https://ark.cn-beijing.volces.com/api/v3"),
    )

    ctx := context.Background()

    fmt.Println("----- standard request -----")
    req := model.CreateChatCompletionRequest{
       // 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
       Model: "ep-20251220162528-gxhqh",
       Messages: []*model.ChatCompletionMessage{
          {
             Role: model.ChatMessageRoleSystem,
             Content: &model.ChatCompletionMessageContent{
                StringValue: volcengine.String("你是人工智能助手."),
             },
          },
          {
             Role: model.ChatMessageRoleUser,
             Content: &model.ChatCompletionMessageContent{
                StringValue: volcengine.String("你好"),
             },
          },
       },
    }

    resp, err := client.CreateChatCompletion(ctx, req)
    if err != nil {
       fmt.Printf("standard chat error: %v\n", err)
       return
    }
    fmt.Println(*resp.Choices[0].Message.Content.StringValue)

    fmt.Println("----- streaming request -----")

    req = model.CreateChatCompletionRequest{
       // 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
       Model: "ep-20251220162528-gxhqh",
       Messages: []*model.ChatCompletionMessage{
          {
             Role: model.ChatMessageRoleSystem,
             Content: &model.ChatCompletionMessageContent{
                StringValue: volcengine.String("你是人工智能助手."),
             },
          },
          {
             Role: model.ChatMessageRoleUser,
             Content: &model.ChatCompletionMessageContent{
                StringValue: volcengine.String("你好"),
             },
          },
       },
    }
    stream, err := client.CreateChatCompletionStream(ctx, req)
    if err != nil {
       fmt.Printf("stream chat error: %v\n", err)
       return
    }
    defer stream.Close()

    for {
       recv, err := stream.Recv()
       if err == io.EOF {
          return
       }
       if err != nil {
          fmt.Printf("Stream chat error: %v\n", err)
          return
       }

       if len(recv.Choices) > 0 {
          fmt.Print(recv.Choices[0].Delta.Content)
       }
    }
}

1.1.3.3 Java
请按如下命令安装环境
<dependency>
  <groupId>com.volcengine</groupId>
  <artifactId>volcengine-java-sdk-ark-runtime</artifactId>
  <version>LATEST</version>
</dependency>
请参考如下示例代码进行调用
package com.volcengine.ark.runtime;

import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.service.ArkService;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// 请确保您已将 API Key 存储在环境变量 ARK_API_KEY 中
// 初始化Ark客户端，从环境变量中读取您的API Key
public class ChatCompletionsExample {
    // 从环境变量中获取您的 API Key。此为默认方式，您可根据需要进行修改
    static String apiKey = System.getenv("ARK_API_KEY");
    // 此为默认路径，您可根据业务所在地域进行配置
    static String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
    static ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
    static Dispatcher dispatcher = new Dispatcher();
    static ArkService service = ArkService.builder().dispatcher(dispatcher).connectionPool(connectionPool).baseUrl(baseUrl).apiKey(apiKey).build();

    public static void main(String[] args) {
        System.out.println("\n----- standard request -----");
        final List<ChatMessage> messages = new ArrayList<>();
        final ChatMessage systemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content("你是人工智能助手.").build();
        final ChatMessage userMessage = ChatMessage.builder().role(ChatMessageRole.USER).content("你好").build();
        messages.add(systemMessage);
        messages.add(userMessage);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                // 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
                .model("ep-20251220162528-gxhqh")
                .messages(messages)
                .build();

        service.createChatCompletion(chatCompletionRequest).getChoices().forEach(choice -> System.out.println(choice.getMessage().getContent()));

        System.out.println("\n----- streaming request -----");
        final List<ChatMessage> streamMessages = new ArrayList<>();
        final ChatMessage streamSystemMessage = ChatMessage.builder().role(ChatMessageRole.SYSTEM).content("你是人工智能助手.").build();
        final ChatMessage streamUserMessage = ChatMessage.builder().role(ChatMessageRole.USER).content("你好").build();
        streamMessages.add(streamSystemMessage);
        streamMessages.add(streamUserMessage);

        ChatCompletionRequest streamChatCompletionRequest = ChatCompletionRequest.builder()
                // 指定您创建的方舟推理接入点 ID，此处已帮您修改为您的推理接入点 ID
                .model("ep-20251220162528-gxhqh")
                .messages(messages)
                .build();

        service.streamChatCompletion(streamChatCompletionRequest)
                .doOnError(Throwable::printStackTrace)
                .blockingForEach(
                        choice -> {
                            if (choice.getChoices().size() > 0) {
                                System.out.print(choice.getChoices().get(0).getMessage().getContent());
                            }
                        }
                );

        service.shutdownExecutor();
    }

}

1.2 魔搭社区免费LLM资源
1.2.1 免费 API 调用
多达2万+模型每日2000次免费调用！
支持热门模型如 Qwen、DeepSeek、GLM、MiniMax！
覆盖大语言模型，多模态模型，文生图等多个领域。
[图片]
完整 API 列表可前往【魔搭社区】-【模型库】-【支持体验】-【推理API-Inference】查看：https://modelscope.cn/models?filter=inference_type&page=2&tabKey=task

1.2.2 免费部署应用（提供 GPU/CPU 资源）
ModelScope xGPU 永久免费！
无需付费/订阅，注册魔搭即可免费使用高性能 GPU 资源，托管 AI 应用服务。
xGPU 兼容各种常见的推理引擎、Python 版本、镜像版本（目前支持 Gradio/Streamlit 接入SDK），无需修改任何应用代码，即可通过 xGPU 部署应用。
[图片]
具体使用教程可查看：https://modelscope.cn/learn/705?pid=704

1.2.3更多资源
海量开源模型、数据集、MCP 等免费下载和使用！
模型库：https://modelscope.cn/models
数据集：https://modelscope.cn/datasets
MCP：https://www.modelscope.cn/mcp
2. ASR接入信息
本互动中暂不提供官方 ASR用量，您可以通过以下方式获取ASR的（免费）使用额度
2.1 腾讯ASR
特点：有免费额度
效果：⭐⭐⭐
官方地址：https://console.cloud.tencent.com/asr
免费额度：
[图片]
接入文档：
1. 实时语音识别（WebSocket）：https://cloud.tencent.com/document/product/1093/48982
2. 实时语音识别（SDK）：https://cloud.tencent.com/document/product/1093/52554


2.2 讯飞ASR
特点：注册实名后有免费额度
效果：⭐⭐⭐
官方地址：https://console.xfyun.cn/services/rta
个人认证获得的免费额度：
[图片]

还可参与讯飞官方服务获得更多额度：https://www.xfyun.cn/customerLevel?ch=beginner2


2.3 火山豆包语音识别
计费概述：https://www.volcengine.com/docs/6561/1359369?lang=zh

2.3.1 大模型版本
接入文档：https://www.volcengine.com/docs/6561/80818?lang=zh

2.3.2 标准版本
接入文档：https://www.volcengine.com/docs/6561/80818?lang=zh


2.4 阿里
2.4.1 云ASR
官方接入文档：https://help.aliyun.com/zh/isi/getting-started/sdk-and-api-references?spm=5176.12061031.J_9382622650.3.11aa6822m8ckuT
免费政策：新用户开通服务享有3个月免费试用
[图片]


2.4.2 阿里百炼ASR
官方地址：https://bailian.console.aliyun.com/?tab=model#/efm/model_experience_center/voice
免费政策：https://help.aliyun.com/zh/model-studio/new-free-quota
[图片]

2.5 其它
服务商
免费额度详情
国内可用性及注意事项
Microsoft Azure Speech to Text (语音服务)
免费层 (F0) ：
• 语音转文本：每月5小时音频（标准 & 自定义模型共享）
• 文本转语音：每月50万字符（神经网络声音）
• 其他免费额度：说话人识别每月1万次事务等。
✅ 可直接使用 <br>Azure 在中国大陆由世纪互联运营 (portal.azure.cn)，提供与全球区相同的免费层 (F0) 。注册时区域选择“东亚 (East Asia)”即可，网络访问稳定。
Amazon Transcribe (AWS)

免费套餐：
• 每月60分钟音频转录（为期12个月，自首次请求起算）。
• 适用于流式转录和批量转录。
⚠️ 条件可用
AWS 在中国设有宁夏和北京区域，且官方定价页面显示了中国（宁夏）区域的定价。但免费套餐是否自动适用于中国区域，需在控制台确认。通常，AWS 免费套餐可能仅限全球区域，中国区可能需要单独开通付费账户。

