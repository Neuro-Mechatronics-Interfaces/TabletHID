import { Router } from 'express';
import { uploadRateLimiter, betaRateLimiter, bodySizeLimit, securityHeaders } from './middleware.js';
import { listConfigs, getConfig, getConfigGraphRoute, patchConfigGraphCluster, postConfigGraphCluster, uploadConfig } from './routes/configs.js';
import { adminMe, adminListConfigs, adminGetConfig, adminPatchConfig, adminDeleteConfig } from './routes/admin.js';
import { requireAdmin } from './adminAuth.js';
import { betaSignup } from './routes/beta.js';
import { createDevice, listDevices } from './routes/devices.js';

const router = Router();
router.use(securityHeaders);

router.get('/configs',     listConfigs);
router.get('/configs/:id/graph', getConfigGraphRoute);
router.post('/graph/clusters', bodySizeLimit, postConfigGraphCluster);
router.patch('/graph/clusters/:id', bodySizeLimit, patchConfigGraphCluster);
router.get('/configs/:id', getConfig);
router.post('/configs',    bodySizeLimit, uploadRateLimiter, uploadConfig);
router.get('/devices', listDevices);
router.post('/devices', bodySizeLimit, createDevice);

router.post('/beta-signup', bodySizeLimit, betaRateLimiter, betaSignup);

router.get('/admin/me',           requireAdmin, adminMe);
router.get('/admin/configs',      requireAdmin, adminListConfigs);
router.get('/admin/configs/:id',  requireAdmin, adminGetConfig);
router.patch('/admin/configs/:id', requireAdmin, bodySizeLimit, adminPatchConfig);
router.delete('/admin/configs/:id', requireAdmin, adminDeleteConfig);

export default router;
