package io.github.chanyanny.rag.test;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;


@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RAGTest {

    @Resource
    private OllamaChatModel ollamaChatModel;


    @Resource
    private VectorStore pineconeVectorStore;



    @Test
    public void upload() {
        // 读取文件
        TikaDocumentReader reader = new TikaDocumentReader("knowledge/file.txt");
        List<Document> documents = reader.read();

        // 分割文件
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitDocuments = splitter.apply(documents);

        // 设置文件标签，这是多知识库隔离的关键
        splitDocuments.forEach(doc -> {
            doc.getMetadata().put("knowledgeTag", "Test");
        });

        pineconeVectorStore.add(splitDocuments);

        log.info("知识库文件上传成功");

    }

    @Test
    public void chat() {
        
        PromptTemplate customPromptTemplate = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
                .template("""
                    <query>

                    Context information is below.

			        ---------------------
			        <question_answer_context>
			        ---------------------

			        Given the context information and no prior knowledge, answer the query.

			        Follow these rules:

			            1. If the answer is not in the context, just say that you don't know.
			            2. Avoid statements like "Based on the context..." or "The provided information...".
                        3. Your reply must be in Chinese.
                """)
            .build();

        String question = "陈洋的年龄是多少？就读于哪所学校？";

        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(pineconeVectorStore)
            .promptTemplate(customPromptTemplate)
            .searchRequest(SearchRequest.builder().topK(6).filterExpression("knowledgeTag == 'Test'").build())
            .build();

        String response = ChatClient.builder(ollamaChatModel).build()
            .prompt(question)
            .advisors(qaAdvisor)
            .call()
            .content();

        log.info("question: {}", question);
        log.info("response: {}", response);

    }

}
