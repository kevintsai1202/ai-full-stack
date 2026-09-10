import test from 'node:test';
import assert from 'node:assert/strict';
import { canCompleteTask } from './task-rules-stub.js';

test('紅燈示範：前置任務完成時應允許後續任務執行', () => {
  const tasks = [
    { id: 1, status: 'DONE', dependsOnTaskId: null },
    { id: 2, status: 'TODO', dependsOnTaskId: 1 },
  ];
  assert.equal(canCompleteTask(tasks[1], tasks), true);
});
