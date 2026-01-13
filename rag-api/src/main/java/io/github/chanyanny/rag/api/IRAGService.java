package io.github.chanyanny.rag.api;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import io.github.chanyanny.rag.api.response.Response;

public interface IRAGService {

    /**
     * 查询 RAG 标签列表
     * 
     * @return 标签列表
     * @throws Exception
     */
    Response<List<String>> queryRAGTaglist();

    /**
     * 上传文件
     * 
     * @param tag 标签
     * @param files 文件列表
     * @return 
     * @throws Exception
     */
    Response<String> uploadFile(String tag, List<MultipartFile> files);


    /**
     * 分析 Git 仓库
     * 
     * @param repoURL 仓库 URL
     * @param token 仓库 token
     * @return 
     * @throws Exception
     */
    Response<String> analyzeGitRepository(String repoURL, String token) throws Exception;

}
