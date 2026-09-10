import { test, expect } from '@playwright/test';

test('前置任務完成後，後續任務解除 BLOCKED', async ({ page }) => {
  await page.goto('/task-tracker.html');
  await page.screenshot({ path: 'artifacts/e2e/step-01-initial.png', fullPage: true });

  const blockedTask = page.getByTestId('task-2');
  await expect(blockedTask).toContainText('狀態：BLOCKED');
  await expect(blockedTask.getByRole('button', { name: '標記完成' })).toBeDisabled();
  await page.screenshot({ path: 'artifacts/e2e/step-02-blocked.png', fullPage: true });

  await page.getByTestId('task-1').getByRole('button', { name: '標記完成' }).click();
  await page.screenshot({ path: 'artifacts/e2e/step-03-prerequisite-done.png', fullPage: true });

  await expect(blockedTask).toContainText('狀態：TODO');
  await expect(blockedTask.getByRole('button', { name: '標記完成' })).toBeEnabled();
  await expect(page.locator('#progress')).toHaveText('完成進度：50%');
  await expect(page.locator('#next-task')).toHaveText('下一步：建立阻塞狀態呈現');

  await blockedTask.getByRole('button', { name: '標記完成' }).click();
  await page.screenshot({ path: 'artifacts/e2e/step-04-all-done.png', fullPage: true });
  await expect(page.locator('#progress')).toHaveText('完成進度：100%');
  await expect(page.locator('#next-task')).toHaveText('下一步：全部完成');
});
