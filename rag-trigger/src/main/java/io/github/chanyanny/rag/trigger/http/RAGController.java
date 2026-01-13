package io.github.chanyanny.rag.trigger.http;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.PathResource;
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

    /**
     * 分析 Git 仓库并上传到向量数据库
     * 
     * @param repoURL GitHub 仓库 URL
     * @param token GitHub Personal Access Token
     * @return 分析结果
     */
    @Override
    @RequestMapping(value = "/analyzeGitRepository", method = RequestMethod.POST)
    public Response<String> analyzeGitRepository(@RequestParam("repoURL") String repoURL, @RequestParam("token") String token) {
        // 1. 参数校验
        if (repoURL == null || repoURL.trim().isEmpty()) {
            return Response.<String>builder()
                    .code("400")
                    .info("仓库 URL 不能为空")
                    .build();
        }
        
        if (token == null || token.trim().isEmpty()) {
            return Response.<String>builder()
                    .code("400")
                    .info("GitHub Token 不能为空")
                    .build();
        }
        
        String startPath = null;
        
        try {
            // 2. 提取项目名称
            final String projectName = githubProjectName(repoURL);
            
            // 3. 创建唯一的临时目录（避免并发冲突）
            String timestamp = String.valueOf(System.currentTimeMillis());
            startPath = "./clone-repo/" + projectName + "-" + timestamp;
            File cloneDir = new File(startPath);
            
            log.info("开始克隆仓库: {} 到 {}", repoURL, cloneDir.getAbsolutePath());
            
            // 4. 确保目录不存在
            if (cloneDir.exists()) {
                FileUtils.deleteDirectory(cloneDir);
            }
            
            // 5. 克隆仓库（使用 try-with-resources 自动关闭）
            try (Git git = Git.cloneRepository()
                    .setURI(repoURL)
                    .setDirectory(cloneDir)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                    .call()) {
                
                log.info("克隆成功，开始分析文件");

                // 6. 统计信息
                final java.util.concurrent.atomic.AtomicInteger totalFiles = new java.util.concurrent.atomic.AtomicInteger(0);
                final java.util.concurrent.atomic.AtomicInteger processedFiles = new java.util.concurrent.atomic.AtomicInteger(0);
                final java.util.concurrent.atomic.AtomicInteger failedFiles = new java.util.concurrent.atomic.AtomicInteger(0);
                final java.util.concurrent.atomic.AtomicInteger totalDocuments = new java.util.concurrent.atomic.AtomicInteger(0);

                log.info("========== 开始批量上传文件 ==========");
                log.info("起始路径: {}", cloneDir.getAbsolutePath());
                log.info("知识库标签: {}", projectName);

                // 配置分割器（可复用，避免每次创建）
                TokenTextSplitter splitter = new TokenTextSplitter();

                // 遍历仓库中的所有文件
                Files.walkFileTree(Paths.get(startPath), new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // 跳过系统目录
                String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (dirName.equals(".git") ||
                        dirName.equals("node_modules") ||
                        dirName.equals("target") ||
                        dirName.equals(".idea") ||
                        dirName.equals("build")) {
                    log.info("跳过目录: {}", dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                totalFiles.incrementAndGet();

                try {
                    // 文件过滤
                    if (!isValidFile(file, attrs)) {
                        log.debug("跳过文件: {}", file.getFileName());
                        return FileVisitResult.CONTINUE;
                    }

                    log.info("处理文件 [{}/{}]: {}",
                            processedFiles.get() + 1,
                            totalFiles.get(),
                            file.toString());

                    PathResource resource = new PathResource(file);
                    TikaDocumentReader reader = new TikaDocumentReader(resource);
                    List<Document> documents = reader.read();

                    if (documents.isEmpty()) {
                        log.warn("文件内容为空: {}", file);
                        return FileVisitResult.CONTINUE;
                    }

                    // 分割文件
                    List<Document> splitDocuments = splitter.apply(documents);

                    // 设置文件标签，这是多知识库隔离的关键
                    splitDocuments.forEach(doc -> {
                        doc.getMetadata().put("knowledgeTag", projectName);
                        doc.getMetadata().put("fileName", file.getFileName().toString());
                    });

                    pineconeVectorStore.add(splitDocuments);

                    int docCount = splitDocuments.size();
                    totalDocuments.addAndGet(docCount);
                    processedFiles.incrementAndGet();

                    log.info("✓ 文件处理成功: {} (分割为 {} 个文档片段)",
                            file.getFileName(), docCount);

                } catch (Exception e) {
                    failedFiles.incrementAndGet();
                    log.error("✗ 文件处理失败: {}", file, e);
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.error("访问文件失败: {}", file, exc);
                failedFiles.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }

        });

                // 输出统计信息
                log.info("========== 批量上传完成 ==========");
                log.info("总文件数: {}", totalFiles.get());
                log.info("处理成功: {}", processedFiles.get());
                log.info("处理失败: {}", failedFiles.get());
                log.info("跳过文件: {}", totalFiles.get() - processedFiles.get() - failedFiles.get());
                log.info("文档片段总数: {}", totalDocuments.get());
                log.info("=====================================");

                // 7. 存储标签到 Redis Set（自动去重，线程安全）
                Long addResult = redisTemplate.opsForSet().add(ALL_TAGS_KEY, projectName);
                if (addResult != null && addResult > 0) {
                    log.info("新增知识库标签到 Redis: {}", projectName);
                } else {
                    log.info("知识库标签已存在于 Redis: {}", projectName);
                }

                log.info("分析完成，项目: {}", projectName);

                return Response.<String>builder()
                        .code("200")
                        .info("分析完成")
                        .data(String.format("项目: %s, 成功: %d, 失败: %d, 文档片段: %d", 
                            projectName, processedFiles.get(), failedFiles.get(), totalDocuments.get()))
                        .build();
            }
            
        } catch (IllegalArgumentException e) {
            log.error("参数错误: {}", e.getMessage());
            return Response.<String>builder()
                    .code("400")
                    .info("参数错误: " + e.getMessage())
                    .build();
                    
        } catch (org.eclipse.jgit.api.errors.TransportException e) {
            log.error("Git 传输失败，请检查 URL 和 Token: {}", repoURL, e);
            return Response.<String>builder()
                    .code("401")
                    .info("认证失败，请检查 GitHub Token 是否正确")
                    .build();
                    
        } catch (org.eclipse.jgit.api.errors.GitAPIException e) {
            log.error("Git 操作失败: {}", repoURL, e);
            return Response.<String>builder()
                    .code("500")
                    .info("Git 克隆失败: " + e.getMessage())
                    .build();
                    
        } catch (IOException e) {
            log.error("文件操作失败: {}", repoURL, e);
            return Response.<String>builder()
                    .code("500")
                    .info("文件操作失败: " + e.getMessage())
                    .build();
                    
        } catch (Exception e) {
            log.error("未知错误: {}", repoURL, e);
            return Response.<String>builder()
                    .code("500")
                    .info("系统错误: " + e.getMessage())
                    .build();
                    
        } finally {
            // 8. 清理临时目录
            if (startPath != null) {
                try {
                    File cloneDir = new File(startPath);
                    if (cloneDir.exists()) {
                        FileUtils.deleteDirectory(cloneDir);
                        log.info("清理临时目录: {}", startPath);
                    }
                } catch (IOException e) {
                    log.warn("清理临时目录失败: {}", startPath, e);
                }
            }
        }
    }

    /**
     * 判断文件是否需要处理
     */
    private boolean isValidFile(Path file, BasicFileAttributes attrs) {
        // 文件大小限制（10MB）
        if (attrs.size() > 10 * 1024 * 1024) {
            log.warn("文件过大，跳过: {} ({}MB)",
                    file.getFileName(), attrs.size() / 1024 / 1024);
            return false;
        }

        // 空文件
        if (attrs.size() == 0) {
            return false;
        }

        String fileName = file.getFileName().toString().toLowerCase();

        // 支持的文件类型
        return fileName.endsWith(".txt") || fileName.endsWith(".md") ||
                fileName.endsWith(".pdf") || fileName.endsWith(".doc") ||
                fileName.endsWith(".docx") || fileName.endsWith(".java") ||
                fileName.endsWith(".py") || fileName.endsWith(".js") ||
                fileName.endsWith(".ts") || fileName.endsWith(".go") ||
                fileName.endsWith(".html") || fileName.endsWith(".xml") ||
                fileName.endsWith(".json") || fileName.endsWith(".yml") ||
                fileName.endsWith(".yaml");
    }


    /**
     * 获取 GitHub 项目名称
     * 
     * @param repoURL 仓库 URL
     * @return 项目名称
     */
    /**
     * 从 GitHub 仓库 URL 提取项目名称
     * 支持 HTTPS 和 SSH 格式
     * 
     * @param repoURL GitHub 仓库 URL
     * @return 项目名称
     * @throws IllegalArgumentException 如果 URL 格式不正确
     */
    private String githubProjectName(String repoURL) {
        // 匹配 HTTPS 和 SSH 格式
        // https://github.com/user/repo.git
        // git@github.com:user/repo.git
        // https://github.com/user/repo
        
        Pattern pattern = Pattern.compile(
            "(?:https?://github\\.com/|git@github\\.com:)" +  // 协议部分
            "(?:[^/]+/)*" +                                    // 可能的组织层级
            "([^/\\.]+)" +                                     // 项目名称
            "(?:\\.git)?$"                                     // 可选的 .git
        );
        
        Matcher matcher = pattern.matcher(repoURL);
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        throw new IllegalArgumentException("无效的 GitHub URL: " + repoURL);
    }   
}
