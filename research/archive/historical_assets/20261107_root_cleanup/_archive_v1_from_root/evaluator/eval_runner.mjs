#!/usr/bin/env node
/**
 * Guanjia Harness Automated Evaluator (eval_runner.mjs)
 * 运行基准测试集，评估候选架构的准确率、Token效率与响应速度。
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT_DIR = path.resolve(__dirname, '..');

const SUITE_PATH = path.join(ROOT_DIR, 'benchmark', 'test_suite.json');

function loadTestSuite() {
  if (!fs.existsSync(SUITE_PATH)) {
    console.error(`[FATAL] 基准测试集不存在: ${SUITE_PATH}`);
    process.exit(1);
  }
  return JSON.parse(fs.readFileSync(SUITE_PATH, 'utf-8'));
}

export function evaluateResults(caseResults) {
  let totalPoints = 0;
  const maxPoints = caseResults.length * 5;
  let totalTokens = 0;
  let totalLatencyMs = 0;

  const categoryBreakdown = {};

  for (const r of caseResults) {
    totalPoints += (r.score || 0);
    totalTokens += (r.tokens || 0);
    totalLatencyMs += (r.latency_ms || 0);

    const cat = r.category || 'OTHER';
    if (!categoryBreakdown[cat]) {
      categoryBreakdown[cat] = { scored: 0, total: 0, count: 0 };
    }
    categoryBreakdown[cat].scored += (r.score || 0);
    categoryBreakdown[cat].total += 5;
    categoryBreakdown[cat].count += 1;
  }

  // 1. 准确率分 (0 - 100)
  const accuracyScore = Number(((totalPoints / maxPoints) * 100).toFixed(2));

  // 2. Token 效率分 (0 - 100)：基准 20 题预估 40,000 token，每超出 500 token 扣 1 分
  const tokenBaseline = 25000;
  const tokenScore = Math.max(0, Math.min(100, Number((100 - Math.max(0, (totalTokens - tokenBaseline) / 500)).toFixed(2))));

  // 3. 速度分 (0 - 100)：总耗时每 10 秒扣 1 分
  const totalSeconds = totalLatencyMs / 1000;
  const speedScore = Math.max(0, Math.min(100, Number((100 - totalSeconds / 10).toFixed(2))));

  // 综合分加权公式
  const finalScore = Number((accuracyScore * 0.6 + tokenScore * 0.25 + speedScore * 0.15).toFixed(2));

  return {
    final_score: finalScore,
    accuracy_score: accuracyScore,
    token_score: tokenScore,
    speed_score: speedScore,
    metrics: {
      total_points: totalPoints,
      max_points: maxPoints,
      total_tokens: totalTokens,
      total_latency_seconds: Number(totalSeconds.toFixed(2)),
      case_count: caseResults.length
    },
    category_breakdown: categoryBreakdown
  };
}

// CLI 直接运行模式
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const suite = loadTestSuite();
  console.log(`====================================================`);
  console.log(`   Guanjia Harness 基准测试加载完毕 (共 ${suite.length} 题)`);
  console.log(`====================================================`);

  if (args[0] === '--dry-run') {
    // 模拟基线打分
    console.log(`[DRY-RUN] 正在对当前 Baseline 进行模拟评估...`);
    const mockResults = suite.map((tc, idx) => ({
      id: tc.id,
      category: tc.category,
      score: idx < 12 ? 4 : (idx < 17 ? 3 : 2), // 模拟未调优前的典型得分
      tokens: 1200 + Math.floor(Math.random() * 600),
      latency_ms: 1500 + Math.floor(Math.random() * 2000),
      notes: "Baseline default response"
    }));

    const report = evaluateResults(mockResults);
    console.log(`\n--- 评估结果摘要 ---`);
    console.log(`综合评分: ${report.final_score} / 100 (达标线: 80.00)`);
    console.log(`准确率分: ${report.accuracy_score} | Token效率分: ${report.token_score} | 响应速度分: ${report.speed_score}`);
    console.log(`总 Token 消耗: ${report.metrics.total_tokens} | 总延迟: ${report.metrics.total_latency_seconds}s\n`);
    console.log(`分类明细:`);
    for (const [k, v] of Object.entries(report.category_breakdown)) {
      console.log(`  - ${k}: ${v.scored}/${v.total} 分 (${((v.scored/v.total)*100).toFixed(1)}%)`);
    }
  } else {
    console.log(`用法: node evaluator/eval_runner.mjs --dry-run`);
    console.log(`或者在自循环脚本中 import { evaluateResults } 进行自动化断言调用。`);
  }
}
