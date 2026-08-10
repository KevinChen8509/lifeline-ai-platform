// Week 3 · AI Agent 对比页（Vue 3 CDN + Element Plus）
// A 路 = CustomerInsightAgent（走 /api/v1/* + 治理：脱敏 + 审计 + 血缘）
// B 路 = RawDbAgent（直接 JDBC，无治理 → 用于对比展示 PII 泄漏风险）

const { createApp, ref, computed } = Vue;

const PRESET_QUESTIONS = [
  { label: 'C0001 画像', q: '查询客户 C0001 的完整画像' },
  { label: 'VIP3 数量',  q: 'VIP3 等级的客户有多少？' },
  { label: '高风险预警', q: '哪些客户是高风险？列出前 5 个' },
  { label: 'C0001 订单', q: '查询客户 C0001 的订单数据' },
  { label: 'C0002 画像', q: '查询客户 C0002 的画像和电话号码' },
];

const app = createApp({
  setup() {
    const question = ref(PRESET_QUESTIONS[0].q);
    const lastQuestion = ref('');
    const fabric = ref({ loading: false, answer: '', error: '', elapsedMs: 0, piiLeak: false });
    const raw    = ref({ loading: false, answer: '', error: '', elapsedMs: 0, piiLeak: false });

    // 简单 PII 探测：phone/id_card/email 在答案里出现就视为泄漏
    const PII_PATTERNS = [
      /\b1[3-9]\d{9}\b/g,                       // mobile
      /\b\d{17}[\dXx]\b/g,                       // id card
      /[\w.+-]+@[\w-]+\.[\w.-]+/g,               // email
    ];
    function detectPii(text) {
      if (!text) return false;
      return PII_PATTERNS.some((p) => p.test(text));
    }

    async function callEndpoint(path, stateRef) {
      stateRef.value = { loading: true, answer: '', error: '', elapsedMs: 0, piiLeak: false };
      const t0 = Date.now();
      try {
        const res = await fetch(path, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ question: question.value }),
        });
        const body = await res.json();
        const answer = body.answer || body.error || '(无回答)';
        stateRef.value = {
          loading: false,
          answer,
          error: body.error ? body.error : '',
          elapsedMs: body.elapsedMs || (Date.now() - t0),
          piiLeak: detectPii(answer),
        };
      } catch (err) {
        stateRef.value = {
          loading: false,
          answer: '',
          error: err.message || '网络错误',
          elapsedMs: Date.now() - t0,
          piiLeak: false,
        };
      }
    }

    async function askBoth(q) {
      if (q) question.value = q;
      if (!question.value.trim()) return;
      lastQuestion.value = question.value;
      // 并发两路调用
      await Promise.all([
        callEndpoint('/api/w3/ask-fabric', fabric),
        callEndpoint('/api/w3/ask-raw', raw),
      ]);
    }

    function usePreset(p) {
      question.value = p.q;
      askBoth();
    }

    const fabricAnswerClass = computed(() =>
      fabric.value.loading ? '' : fabric.value.error ? 'err' : fabric.value.answer ? '' : 'empty'
    );
    const rawAnswerClass = computed(() =>
      raw.value.loading ? '' : raw.value.error ? 'err' : raw.value.answer ? '' : 'empty'
    );

    return {
      PRESET_QUESTIONS,
      question,
      lastQuestion,
      fabric,
      raw,
      askBoth,
      usePreset,
      fabricAnswerClass,
      rawAnswerClass,
    };
  },
  template: `
    <div>
      <header class="page-header">
        <div>
          <h1>Week 3 · AI Agent 对比</h1>
          <div class="sub">A 路治理（脱敏+审计+血缘） vs B 路直查（无治理 · PII 泄漏对照）</div>
        </div>
        <a href="/">← 回主页</a>
      </header>

      <div class="container">
        <div class="input-row">
          <input
            v-model="question"
            placeholder="输入问题，例如：查询客户 C0001 的画像"
            @keyup.enter="askBoth()"
          />
          <button :disabled="fabric.loading || raw.loading" @click="askBoth()">
            <span v-if="fabric.loading || raw.loading">调用中…</span>
            <span v-else>同时提问 A/B 两路</span>
          </button>
        </div>

        <div class="question-bar">
          <button
            v-for="p in PRESET_QUESTIONS"
            :key="p.label"
            class="q-btn"
            @click="usePreset(p)"
          >{{ p.label }}</button>
        </div>

        <div class="split">
          <!-- A 路 -->
          <section class="pane fabric">
            <div class="pane-header">
              <span class="pane-title">A 路 · CustomerInsightAgent</span>
              <span class="pane-tag fabric">治理 + 脱敏</span>
            </div>
            <div class="pane-meta">
              <span>耗时 <b>{{ fabric.elapsedMs }} ms</b></span>
              <span>
                PII 状态：
                <span v-if="fabric.answer" class="pii-flag safe">{{ fabric.piiLeak ? '检测到 PII' : '已脱敏 ✓' }}</span>
                <span v-else>—</span>
              </span>
            </div>
            <div :class="['answer-box', fabricAnswerClass]">
              <span v-if="fabric.loading"><span class="loader"></span>LLM 思考中…</span>
              <template v-else>{{ fabric.answer || (fabric.error ? '✗ ' + fabric.error : '点击按钮发起提问') }}</template>
            </div>
          </section>

          <!-- B 路 -->
          <section class="pane raw">
            <div class="pane-header">
              <span class="pane-title">B 路 · RawDbAgent</span>
              <span class="pane-tag raw">直接 JDBC · 无治理</span>
            </div>
            <div class="pane-meta">
              <span>耗时 <b>{{ raw.elapsedMs }} ms</b></span>
              <span>
                PII 状态：
                <span v-if="raw.answer" :class="['pii-flag', raw.piiLeak ? '' : 'safe']">{{ raw.piiLeak ? '⚠ 明文泄漏' : '未检测到' }}</span>
                <span v-else>—</span>
              </span>
            </div>
            <div :class="['answer-box', rawAnswerClass]">
              <span v-if="raw.loading"><span class="loader"></span>LLM 思考中…</span>
              <template v-else>{{ raw.answer || (raw.error ? '✗ ' + raw.error : '点击按钮发起提问') }}</template>
            </div>
          </section>
        </div>

        <div class="legend">
          <b>A 路</b> 调用 LangChain4j Agent，工具内部走 <code>/api/v1/*</code>，触发 DataMaskingAspect + LoggingAuditLogger + LineageAspect，
          返回的电话/身份证已脱敏（如 <code>138****1234</code>）。<br/>
          <b>B 路</b> 调用 LangChain4j Agent，工具直接 JDBC 查 MySQL/ClickHouse/PostgreSQL，
          返回的字段是数据库原始值（电话/身份证明文）—— 用于对照演示"绕过治理"的风险。
        </div>
      </div>
    </div>
  `,
});

app.use(ElementPlus);
app.mount('#app');
