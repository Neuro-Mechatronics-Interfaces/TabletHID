import { Router } from 'express';
import { uploadRateLimiter, bodySizeLimit, securityHeaders } from './middleware.js';
import { listConfigs, getConfig, uploadConfig } from './routes/configs.js';
import { adminMe, adminListConfigs, adminGetConfig, adminPatchConfig, adminDeleteConfig } from './routes/admin.js';
import { requireAdmin } from './adminAuth.js';

const router = Router();
router.use(securityHeaders);

router.get('/configs',     listConfigs);
router.get('/configs/:id', getConfig);
router.post('/configs',    bodySizeLimit, uploadRateLimiter, uploadConfig);

router.get('/admin/me',           requireAdmin, adminMe);
router.get('/admin/configs',      requireAdmin, adminListConfigs);
router.get('/admin/configs/:id',  requireAdmin, adminGetConfig);
router.patch('/admin/configs/:id', requireAdmin, bodySizeLimit, adminPatchConfig);
router.delete('/admin/configs/:id', requireAdmin, adminDeleteConfig);

export default router;
