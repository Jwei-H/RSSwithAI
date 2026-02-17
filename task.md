# 任务-搜索优化
## 目标
重构搜索相关代码，提高效率并优化用户体验。

## 要点
1. 前台用户搜索场景，需要支持在某个具体的订阅源中搜索，**仅针对rss类型的源**，若用户在浏览topic源时搜索，直接使用默认的范围("ALL")
2. 后端需要:
  - 修改FrontArticleController#searchArticles，支持新的搜索场景。
  - 引入jieba分词，使用jieba-analysis库（我已引入），其支持tfidf算法提取关键词，我们提取出query中top1的关键词，同时对query和该关键词进行模糊匹配。示例：
  ```
    public static void main(String[] args)
    {
        String content="孩子上了幼儿园 安全防拐教育要做好";
        int topN=5;
        TFIDFAnalyzer tfidfAnalyzer=new TFIDFAnalyzer();
        List<Keyword> list=tfidfAnalyzer.analyze(content,topN);
        for(Keyword word:list)
            System.out.println(word.getName()+":"+word.getTfidfvalue()+",");
        // 防拐:0.1992,幼儿园:0.1434,做好:0.1065,教育:0.0946,安全:0.0924
    }
  ```
  - 将模糊匹配召回和向量召回并行化，缩短rt
3. 前端仅涉及用户前台部分(fronted-user)，除了适配上述功能，还要修复一个case：
  - 点击搜索后，地址栏url能看到搜索内容，这很好。但是此时点击浏览器回退功能，地址栏虽发生变化，但依然展示的是搜索内容。同样的问题在订阅页和频道页都存在

4. 修改完成后，请将相关代码变动反映到相关文档。