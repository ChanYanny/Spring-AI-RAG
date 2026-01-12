package io.github.chanyanny.rag.trigger.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.chanyanny.rag.api.IRAGService;
import io.github.chanyanny.rag.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/rag")
public class RAGController implements IRAGService {

    @Resource
    private OllamaChatModel ollamaChatModel;


    @Resource
    private VectorStore pineconeVectorStore;


    @Resource
    private RedisTemplate<String, String> redisTemplate;

    private static final String ALL_TAGS_KEY = "rag:all_tags";

    /**
     * 查询知识库标签列表
     * Redis 中使用 Set 存储（自动去重），返回时转为 List
     */
    
    @Override
    @GetMapping("/queryRAGTaglist")
    public Response<List<String>> queryRAGTaglist() {
        log.info("开始查询知识库标签列表");
        
        // 从 Redis Set 中获取所有标签
        Set<String> tagSet = redisTemplate.opsForSet().members(ALL_TAGS_KEY);
        
        // 转换为 List（接口返回类型要求）
        List<String> tags = tagSet != null ? new ArrayList<>(tagSet) : new ArrayList<>();
        
        log.info("查询知识库标签列表成功，共 {} 个标签", tags.size());
        
        return Response.<List<String>>builder()
                .code("200")
                .info("查询知识库标签列表成功")
                .data(tags)
                .build();
    }

    /**
     * 上传文件到知识库
     * 1. 读取文件并分块
     * 2. 存储到向量数据库
     * 3. 将标签存入 Redis Set
     */
    @Override
    @RequestMapping(value = "/uploadFile", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
    public Response<String> uploadFile(@RequestParam("tag") String tag, @RequestParam("files") List<MultipartFile> files) {
        log.info("开始上传知识库，标签: {}, 文件数量: {}", tag, files.size());
        
        try {
            // 文件分割器
            TokenTextSplitter splitter = new TokenTextSplitter();
            int totalDocuments = 0;

            // 处理每个文件
            for (MultipartFile file : files) {
                log.info("处理文件: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());
                
                // 读取文件
                TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
                List<Document> documents = reader.read();

                // 分割文档
                List<Document> splitDocuments = splitter.apply(documents);

                // 设置元数据标签
                splitDocuments.forEach(doc -> {
                    doc.getMetadata().put("knowledgeTag", tag);
                    doc.getMetadata().put("fileName", file.getOriginalFilename());
                });

                // 存储到向量数据库
                pineconeVectorStore.add(splitDocuments);
                
                totalDocuments += splitDocuments.size();
                log.info("文件 {} 处理完成，分割为 {} 个文档片段", file.getOriginalFilename(), splitDocuments.size());
            }

            log.info("所有文件上传完成，共 {} 个文档片段", totalDocuments);

            // 存储标签到 Redis Set（自动去重，线程安全）
            Long addResult = redisTemplate.opsForSet().add(ALL_TAGS_KEY, tag);
            if (addResult != null && addResult > 0) {
                log.info("新增知识库标签到 Redis: {}", tag);
            } else {
                log.info("知识库标签已存在于 Redis: {}", tag);
            }

            return Response.<String>builder()
                    .code("200")
                    .info("上传知识库成功")
                    .data("共上传 " + files.size() + " 个文件，" + totalDocuments + " 个文档片段")
                    .build();
                    
        } catch (Exception e) {
            log.error("上传知识库失败，标签: {}", tag, e);
            return Response.<String>builder()
                    .code("500")
                    .info("上传知识库失败: " + e.getMessage())
                    .build();
        }
    }
}
