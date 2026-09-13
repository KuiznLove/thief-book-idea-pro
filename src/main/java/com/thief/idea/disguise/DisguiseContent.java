package com.thief.idea.disguise;

/**
 * 伪装素材库：把小说正文包装成"AI 编码助手的回复"所需的全部话术与代码片段。
 * <p>
 * 所有内容均为静态常量，按页码派生的种子（seed）确定性选取：
 * 同一页每次渲染结果完全一致（刷新/老板键恢复不会闪烁变化），翻页后又会换一批，
 * 看起来就像助手针对不同问题给出了不同的回复。
 **/
public final class DisguiseContent {

    private DisguiseContent() {
    }

    /**
     * 输入框右下角可选的模型名（纯装饰，不会真的调用任何模型）
     **/
    public static final String[] MODELS = {
            "Seed-Code", "GPT-5.1", "Claude Sonnet 4.5", "DeepSeek-V3.2", "Gemini 3 Pro", "Qwen3-Coder"
    };

    /**
     * 伪装代码卡片的"目标语言"：每套语言各有一组代码片段，
     * 切换后整页的 diff 卡片与文件角标都会跟着换（设置页可选，见 {@link #LANGUAGES}）
     **/
    public static final String PYTHON = "Python";
    public static final String JAVA = "Java";
    public static final String VUE = "Vue";

    /**
     * 设置页下拉的顺序，也是合法取值的白名单
     **/
    public static final String[] LANGUAGES = {PYTHON, JAVA, VUE};

    /**
     * 默认语言：老配置里没有这个字段时用它，与改造前的素材保持一致
     **/
    public static final String DEFAULT_LANGUAGE = PYTHON;

    /**
     * 助手"回复"的开场白：交代这次做了什么（正文会接在它下面）
     **/
    private static final String[] INTROS = {
            "我已经为相关文件创建了修改预览。以下是本次改动的详细说明：",
            "我检查了当前工作区的上下文，定位到几处需要调整的地方，改动预览如下：",
            "根据你的描述，我找到了对应实现，修改内容如下：",
            "已完成修改并跑通本地校验，下面是这次调整的说明：",
            "我复现了这个问题，根因在归一化分支，修改预览如下：",
            "我把相关模块过了一遍，先给出这次改动的核心部分：",
            "已经按你的要求完成改动，下面是实现细节："
    };

    /**
     * 正文中间插入的小标题（模拟 Markdown 二级标题）
     **/
    private static final String[] SECTION_TITLES = {
            "修改总结", "变更详情", "实现说明", "注意事项", "关键改动", "补充说明", "影响范围"
    };

    /**
     * 助手"回复"的结尾话术
     **/
    private static final String[] CLOSINGS = {
            "以上改动已同步到工作区，如需继续调整阈值或补充测试，随时告诉我。",
            "如果这个改动符合预期，我可以接着处理同一模块的其它调用点。",
            "修改已应用，本地单测通过。需要我再补一份回归测试吗？",
            "以上就是本次修改的全部内容，有疑问可以继续追问。",
            "改动已经落地，我继续盯着后续的构建结果，有问题会第一时间同步。",
            "如果需要，我可以把这个改动整理成一条变更记录。"
    };

    /**
     * 用户在输入框里随便打字时，助手"假装"给出的回答
     **/
    private static final String[] REPLIES = {
            "好的，我先读取相关文件再给出结论。",
            "明白，我这就去检查这个模块的调用链。",
            "收到，我基于当前上下文继续分析。",
            "可以，我先把改动点整理成一份清单。",
            "我看了下，这个位置还需要结合上游数据一起判断，稍等。",
            "已经在处理了，完成后我会给出具体改动。"
    };

    /**
     * 伪装操作（复制/应用/添加上下文）的提示文案
     **/
    public static final String TOAST_COPIED = "已复制到剪贴板";
    public static final String TOAST_APPLIED = "已应用到工作区";
    public static final String TOAST_ADDED = "已添加到上下文";
    public static final String TOAST_PAGE_JUMP = "已定位到第 ";
    public static final String TOAST_PAGE_OUT = "超出范围，当前共 ";
    public static final String TOAST_PAGES = " 页";
    public static final String TOAST_THINKING = "正在分析工作区上下文…";
    public static final String TOAST_RUNNING = "已在终端运行";
    public static final String TOAST_BOOK = "请在设置中配置工作区目录";

    /**
     * 未配置书本时窗口里的欢迎语，伪装成助手的自我介绍
     **/
    public static final String WELCOME = "你好，我是你的 AI 编码助手。\n"
            + "还没有可用的工作区上下文，请在设置中配置后再开始。\n"
            + "配置完成后，我会直接给出代码分析和修改建议。";

    /**
     * 代码行类型：上下文 / 新增 / 删除（决定行背景色）
     **/
    public enum Kind {
        CONTEXT, ADD, DEL
    }

    /**
     * 一行代码
     **/
    public static final class CodeLine {
        public final String text;
        public final Kind kind;

        CodeLine(String text, Kind kind) {
            this.text = text;
            this.kind = kind;
        }
    }

    /**
     * 一个伪造的代码片段（带文件名与语言标签）
     **/
    public static final class Snippet {
        public final String fileName;
        public final String badge;
        public final java.util.List<CodeLine> lines;

        Snippet(String fileName, String badge, CodeLine... lines) {
            this.fileName = fileName;
            this.badge = badge;
            this.lines = java.util.Arrays.asList(lines);
        }

        /**
         * 纯文本形式，供"复制"按钮使用
         **/
        public String plainText() {
            StringBuilder sb = new StringBuilder();
            for (CodeLine line : lines) {
                if (line.kind == Kind.ADD) {
                    sb.append("+ ");
                } else if (line.kind == Kind.DEL) {
                    sb.append("- ");
                } else {
                    sb.append("  ");
                }
                sb.append(line.text).append('\n');
            }
            return sb.toString();
        }
    }

    private static CodeLine ctx(String text) {
        return new CodeLine(text, Kind.CONTEXT);
    }

    private static CodeLine add(String text) {
        return new CodeLine(text, Kind.ADD);
    }

    private static CodeLine del(String text) {
        return new CodeLine(text, Kind.DEL);
    }

    /**
     * Python 片段池：常见的 GNN / 训练脚本片段，行长度控制在 64 字符内，
     * 保证在等宽字体下不会折行、看起来像真实的 diff
     **/
    private static final Snippet[] PYTHON_SNIPPETS = {
            new Snippet("models/DSHGCN.py", "PY",
                    ctx("def build_hypergraph(adj_tilde, mask, k=2):"),
                    ctx("    adj_tilde = adj_tilde * mask"),
                    add("    D_sum = adj_tilde.sum(dim=-1).clamp(min=1e-4)"),
                    del("    D_sum = adj_tilde.sum(dim=-1).clamp(min=1e-9)"),
                    add("    D_rsqrt = D_sum ** (-0.5)  # [B, N]"),
                    ctx("    D_rsqrt_mat = torch.diag_embed(D_rsqrt)"),
                    ctx("    return D_rsqrt_mat @ adj_tilde @ D_rsqrt_mat"),
                    ctx(""),
                    add("# D^{-0.5} * A' * D^{-0.5}")),
            new Snippet("trainer.py", "PY",
                    ctx("for step, batch in enumerate(loader):"),
                    ctx("    lr = base_lr * warmup_factor(step, cfg.warmup)"),
                    del("    scheduler.step(epoch)"),
                    add("    scheduler.step_update(step)"),
                    add("    if step % cfg.log_every == 0:"),
                    add("        logger.info(f'step={step} loss={loss:.4f}')"),
                    ctx("    loss = criterion(model(batch), batch.y)"),
                    ctx("    loss.backward()"),
                    ctx("    optimizer.step()")),
            new Snippet("models/attention.py", "PY",
                    ctx("def forward(self, q, k, v, mask=None):"),
                    ctx("    attn = (q @ k.transpose(-2, -1)) * self.scale"),
                    del("    attn = attn.softmax(dim=-1)"),
                    add("    attn = attn.masked_fill(mask == 0, -1e4)"),
                    add("    attn = attn.softmax(dim=-1)"),
                    ctx("    out = attn @ v"),
                    ctx("    return self.proj(out)")),
            new Snippet("utils/metrics.py", "PY",
                    ctx("@torch.no_grad()"),
                    ctx("def evaluate(model, loader):"),
                    ctx("    model.eval()"),
                    ctx("    total, correct = 0, 0"),
                    ctx("    for batch in loader:"),
                    add("        logits = model(batch.x, batch.edge_index)"),
                    ctx("        pred = logits.argmax(dim=-1)"),
                    ctx("        correct += (pred == batch.y).sum().item()"),
                    ctx("        total += batch.y.numel()"),
                    ctx("    return correct / max(total, 1)")),
            new Snippet("configs/gnn_base.yaml", "YAML",
                    ctx("model:"),
                    ctx("  hidden_dim: 128"),
                    ctx("  num_layers: 3"),
                    del("  dropout: 0.5"),
                    add("  dropout: 0.1"),
                    ctx("optim:"),
                    ctx("  lr: 1.0e-3"),
                    del("  weight_decay: 0.0"),
                    add("  weight_decay: 5.0e-4")),
            new Snippet("losses/focal.py", "PY",
                    ctx("def focal_loss(logits, target, gamma=2.0):"),
                    ctx("    logp = F.log_softmax(logits, dim=-1)"),
                    ctx("    logpt = logp.gather(1, target.unsqueeze(1))"),
                    del("    loss = -logpt.mean()"),
                    add("    pt = logpt.exp().clamp(min=1e-6)"),
                    add("    loss = -(1 - pt) ** gamma * logpt"),
                    ctx("    return loss.mean()")),
            new Snippet("data/graph_dataset.py", "PY",
                    ctx("class GraphDataset(InMemoryDataset):"),
                    ctx("    def __init__(self, root, transform=None):"),
                    ctx("        super().__init__(root, transform)"),
                    del("        self.cache = {}"),
                    add("        self.cache = LRUCache(maxsize=1024)"),
                    ctx("        self.data, self.slices = torch.load("),
                    ctx("            self.processed_paths[0])")),
            new Snippet("scripts/export_onnx.py", "PY",
                    ctx("model = build_model(cfg)"),
                    ctx("model.load_state_dict(ckpt['state_dict'])"),
                    ctx("model.eval()"),
                    del("dummy = torch.randn(1, 3, 224, 224)"),
                    add("dummy = torch.randn(1, cfg.num_nodes, cfg.feat_dim)"),
                    ctx("torch.onnx.export("),
                    ctx("    model, dummy, cfg.onnx_path,"),
                    add("    opset_version=17,"),
                    add("    dynamic_axes={'x': {0: 'batch'}})")),
    };

    /**
     * Java 片段池：仿 Spring Boot 智能客服服务
     * （会话管理 / 意图路由 / 知识库检索 / 转人工 / WebSocket 推送）
     **/
    private static final Snippet[] JAVA_SNIPPETS = {
            new Snippet("chat/ChatController.java", "JAVA",
                    ctx("@RestController"),
                    ctx("@RequestMapping(\"/api/chat\")"),
                    ctx("public class ChatController {"),
                    ctx("    private final ChatService chatService;"),
                    del("    @PostMapping(\"/send\")"),
                    add("    @PostMapping(\"/message\")"),
                    add("    public Reply send(@RequestBody ChatRequest req) {"),
                    ctx("        return chatService.handle(req);"),
                    ctx("    }"),
                    ctx("}")),
            new Snippet("chat/ChatService.java", "JAVA",
                    ctx("@Service"),
                    ctx("public class ChatService {"),
                    ctx("    public Reply handle(ChatRequest req) {"),
                    ctx("        Session session = sessionStore.load(req.getSessionId());"),
                    add("        Intent intent = resolveIntent(req, session);"),
                    add("        if (intent.isNeedHuman()) {"),
                    add("            return transferService.transfer(session, intent);"),
                    add("        }"),
                    del("        return replyFactory.generic(req.getText());"),
                    ctx("        return faqService.answer(intent, session);"),
                    ctx("    }"),
                    ctx("}")),
            new Snippet("chat/IntentResolver.java", "JAVA",
                    ctx("@Component"),
                    ctx("public class IntentResolver {"),
                    add("    public Intent resolve(String text, Session session) {"),
                    ctx("        String normalized = text.trim().toLowerCase();"),
                    del("        Rule rule = matcher.match(normalized);"),
                    add("        Rule rule = ruleMatcher.match(normalized, session);"),
                    add("        if (rule == null) {"),
                    add("            return Intent.fallback(session.getLastIntent());"),
                    add("        }"),
                    ctx("        return Intent.of(rule.getCode(), rule.getScore());"),
                    ctx("    }"),
                    ctx("}")),
            new Snippet("faq/FaqRepository.java", "JAVA",
                    ctx("@Repository"),
                    ctx("public interface FaqRepository extends JpaRepository<Faq, Long> {"),
                    ctx("    List<Faq> findByTenantIdAndEnabledTrue(Long tenantId);"),
                    del("    List<Faq> findByQuestionContaining(String kw);"),
                    add("    @Query(\"select f from Faq f where f.enabled = true\")"),
                    add("    List<Faq> searchCandidates(@Param(\"kw\") String kw);"),
                    ctx("    Optional<Faq> findByQuestionIgnoreCase(String q);"),
                    ctx("}")),
            new Snippet("chat/SessionStore.java", "JAVA",
                    ctx("@Component"),
                    ctx("public class SessionStore {"),
                    ctx("    private final RedisTemplate<String, Session> redis;"),
                    ctx("    public Session load(String sessionId) {"),
                    del("        Session s = cache.get(sessionId);"),
                    add("        Session s = redis.opsForValue().get(key(sessionId));"),
                    add("        return s != null ? s : Session.create(sessionId);"),
                    ctx("    }"),
                    ctx("    public void save(Session session) {"),
                    add("        redis.opsForValue().set(key(session), session, TTL);"),
                    ctx("    }"),
                    ctx("}")),
            new Snippet("agent/TransferService.java", "JAVA",
                    ctx("@Service"),
                    ctx("public class TransferService {"),
                    ctx("    public TransferResult transfer(Session s, Intent intent) {"),
                    del("        return TransferResult.queued(s.getId());"),
                    add("        Agent agent = agentQueue.poll(s.getTenantId());"),
                    add("        if (agent == null) {"),
                    add("            agentQueue.enqueue(s.getId());"),
                    add("            return TransferResult.queued(s.getId());"),
                    add("        }"),
                    ctx("        s.setAgentId(agent.getId());"),
                    ctx("        return TransferResult.connected(agent);"),
                    ctx("    }"),
                    ctx("}")),
            new Snippet("config/WebSocketConfig.java", "JAVA",
                    ctx("@Configuration"),
                    ctx("@EnableWebSocketMessageBroker"),
                    ctx("public class WebSocketConfig"),
                    ctx("        implements WebSocketMessageBrokerConfigurer {"),
                    add("    private static final String ENDPOINT = \"/ws/chat\";"),
                    add("    private static final String TOPIC = \"/topic/reply\";"),
                    ctx("}")),
            new Snippet("resources/application.yml", "YAML",
                    ctx("spring:"),
                    ctx("  datasource:"),
                    ctx("    url: jdbc:mysql://127.0.0.1:3306/cs_bot"),
                    ctx("    hikari:"),
                    del("      maximum-pool-size: 5"),
                    add("      maximum-pool-size: 20"),
                    ctx("chat:"),
                    ctx("  session.ttl: 30m"),
                    add("  agent.queue-timeout: 120s")),
    };

    /**
     * Vue 片段池：仿 Vue 3 + TypeScript 的前端项目（组合式 API / Pinia / Vite）
     **/
    private static final Snippet[] VUE_SNIPPETS = {
            new Snippet("components/GraphView.vue", "VUE",
                    ctx("<script setup lang=\"ts\">"),
                    ctx("const props = defineProps<{ nodes: Node[] }>()"),
                    del("const { data } = useGraph(props.nodes)"),
                    add("const { data, loading } = useGraph(toRef(props, 'nodes'))"),
                    add("watch(() => props.nodes, refresh, { immediate: true })"),
                    ctx("</script>")),
            new Snippet("composables/useGraph.ts", "TS",
                    ctx("export function useGraph(source: Ref<Node[]>) {"),
                    ctx("  const data = ref<GraphData | null>(null)"),
                    ctx("  const loading = ref(false)"),
                    ctx("  async function refresh() {"),
                    ctx("    loading.value = true"),
                    del("    data.value = await client.layout()"),
                    add("    data.value = await client.layout(source.value)"),
                    ctx("    loading.value = false"),
                    ctx("  }"),
                    ctx("  return { data, loading, refresh }"),
                    ctx("}")),
            new Snippet("store/graph.ts", "TS",
                    ctx("export const useGraphStore = defineStore('graph', () => {"),
                    ctx("  const nodes = computed(() => state.value.nodes)"),
                    del("  const selected = ref<string>()"),
                    add("  const selected = ref<string | null>(null)"),
                    add("  function select(id: string) { selected.value = id }"),
                    ctx("  return { nodes, selected, select }"),
                    ctx("})")),
            new Snippet("router/index.ts", "TS",
                    ctx("const routes: RouteRecordRaw[] = ["),
                    ctx("  { path: '/', component: Dashboard },"),
                    del("  { path: '/graph/:id', component: GraphPage },"),
                    add("  { path: '/graph/:id', props: true, component: GraphPage },"),
                    add("  { path: '/settings', component: () => import('./Settings') },"),
                    ctx("]")),
            new Snippet("vite.config.ts", "TS",
                    ctx("export default defineConfig({"),
                    ctx("  plugins: [vue()],"),
                    del("  server: { port: 3000 },"),
                    add("  server: { port: 5173, proxy: { '/api': API_URL } },"),
                    add("  resolve: { alias: { '@': fileURLToPath(SRC) } },"),
                    ctx("})")),
            new Snippet("components/NodeList.vue", "VUE",
                    ctx("<template>"),
                    ctx("  <ul class=\"node-list\">"),
                    add("    <li v-for=\"n in nodes\" :key=\"n.id\" @click=\"select(n.id)\">"),
                    ctx("      {{ n.label }}"),
                    ctx("    </li>"),
                    ctx("  </ul>"),
                    ctx("</template>")),
            new Snippet("api/client.ts", "TS",
                    ctx("export const client = {"),
                    ctx("  async layout(nodes: Node[]) {"),
                    del("    const res = await fetch('/api/layout')"),
                    add("    const res = await fetch('/api/layout', {"),
                    add("      method: 'POST', body: JSON.stringify({ nodes }),"),
                    ctx("    })"),
                    ctx("    return res.json() as Promise<GraphData>"),
                    ctx("  },"),
                    ctx("}")),
            new Snippet("views/Dashboard.vue", "VUE",
                    ctx("<script setup lang=\"ts\">"),
                    ctx("const store = useGraphStore()"),
                    ctx("const { data, loading } = useGraph(toRef(store, 'nodes'))"),
                    del("onMounted(refresh)"),
                    add("onMounted(() => { if (!data.value) refresh() })"),
                    ctx("</script>")),
    };

    /**
     * 按语言取片段池：非法/未知语言一律回退到 Python，保证渲染不会因为配置脏数据而崩
     **/
    private static Snippet[] snippets(String language) {
        if (JAVA.equals(language)) {
            return JAVA_SNIPPETS;
        }
        if (VUE.equals(language)) {
            return VUE_SNIPPETS;
        }
        return PYTHON_SNIPPETS;
    }

    /**
     * 按种子取开场白
     **/
    public static String intro(int seed) {
        return INTROS[Math.floorMod(seed, INTROS.length)];
    }

    /**
     * 按序号取小标题（同页多次调用传入不同序号，避免重复）
     **/
    public static String sectionTitle(int seed, int index) {
        return SECTION_TITLES[Math.floorMod(seed / 3 + index * 5, SECTION_TITLES.length)];
    }

    /**
     * 按种子取结尾话术
     **/
    public static String closing(int seed) {
        return CLOSINGS[Math.floorMod(seed / 7, CLOSINGS.length)];
    }

    /**
     * 按种子取"假装"的回答
     **/
    public static String reply(int seed) {
        return REPLIES[Math.floorMod(seed / 11, REPLIES.length)];
    }

    /**
     * 按语言 + 种子取代码片段。
     * <p>
     * index 是页内序号（0 = 顶部那张），由渲染层递增传入。这里用 `+ index` 而不是乘系数，
     * 就能保证同一页里 index 不同必然取到不同片段（每组片段数与页内最大卡片数对齐）。
     **/
    public static Snippet snippet(String language, int seed, int index) {
        Snippet[] pool = snippets(language);
        return pool[Math.floorMod(seed / 13 + index, pool.length)];
    }

    /**
     * 该语言下的片段总数（用于避免同页出现两个相同片段）
     **/
    public static int snippetCount(String language) {
        return snippets(language).length;
    }

    /**
     * 一条伪装的"单行 shell 命令"：脚本名 + 命令本身。
     * <p>
     * 两者是**配对**的（`scripts/test.sh` 里就是跑测试），这样卡片头部和命令行看起来互相印证，
     * 比各自随种子乱取一版可信得多。
     **/
    public static final class ShellScript {
        public final String fileName;
        public final String command;

        ShellScript(String fileName, String command) {
            this.fileName = fileName;
            this.command = command;
        }
    }

    /**
     * 单行 shell 命令池：**独立于 {@link #LANGUAGES} 的第四类素材**，不随"代码语言"切换，
     * 由设置页的 "Shell snippet in paragraphs" 开关单独控制。
     * <p>
     * 渲染层会把它塞进一个自然段的中间（把段落切成两半），所以每条只有一行；
     * 命令不含 `$` 提示符——那个由渲染层用主题色画，好和 diff 卡片区分开。
     * 角标固定 `SH`，文件名统一放在 `scripts/` 下。
     **/
    public static final ShellScript[] SHELL_SCRIPTS = {
            new ShellScript("scripts/build.sh", "./gradlew build -x test"),
            new ShellScript("scripts/test.sh", "python -m pytest -q tests/"),
            new ShellScript("scripts/dev.sh", "npm run dev"),
            new ShellScript("scripts/status.sh", "git status --short"),
            new ShellScript("scripts/diff.sh", "git diff --stat HEAD~1"),
            new ShellScript("scripts/lint.sh", "pnpm lint --fix"),
            new ShellScript("scripts/typecheck.sh", "npx tsc --noEmit"),
            new ShellScript("scripts/deploy.sh", "docker compose up -d"),
            new ShellScript("scripts/health.sh", "curl -s localhost:8080/health"),
            new ShellScript("scripts/k8s.sh", "kubectl get pods -n staging"),
    };

    /**
     * 按序号取一条 shell 命令（同页多次调用传入不同序号，避免重复）
     **/
    public static ShellScript shellScript(int seed, int index) {
        return SHELL_SCRIPTS[Math.floorMod(seed / 17 + index, SHELL_SCRIPTS.length)];
    }
}
