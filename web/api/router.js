import { Router } from 'express';
import { uploadRateLimiter, betaRateLimiter, bodySizeLimit, securityHeaders } from './middleware.js';
import { listConfigs, getConfig, getConfigGraphRoute, uploadConfig } from './routes/configs.js';
import { adminMe, adminListConfigs, adminGetConfig, adminPatchConfig, adminDeleteConfig } from './routes/admin.js';
import { requireAdmin } from './adminAuth.js';
import { betaSignup } from './routes/beta.js';

const router = Router();
router.use(securityHeaders);

router.get('/configs',     listConfigs);
router.get('/configs/:id/graph', getConfigGraphRoute);
router.get('/configs/:id', getConfig);
router.post('/configs',    bodySizeLimit, uploadRateLimiter, uploadConfig);

router.post('/beta-signup', bodySizeLimit, betaRateLimiter, betaSignup);

router.get('/admin/me',           requireAdmin, adminMe);
router.get('/admin/configs',      requireAdmin, adminListConfigs);
router.get('/admin/configs/:id',  requireAdmin, adminGetConfig);
router.patch('/admin/configs/:id', requireAdmin, bodySizeLimit, adminPatchConfig);
router.delete('/admin/configs/:id', requireAdmin, adminDeleteConfig);

export default router;
