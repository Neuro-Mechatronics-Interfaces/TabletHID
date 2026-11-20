import { Router } from 'express';
import { uploadRateLimiter, betaRateLimiter, communityWriteRateLimiter, bodySizeLimit, securityHeaders } from './middleware.js';
import { listConfigs, getConfig, getConfigGraphRoute, patchConfigGraphCluster, postConfigGraphCluster, uploadConfig } from './routes/configs.js';
import { adminMe, adminListConfigs, adminGetConfig, adminPatchConfig, adminDeleteConfig } from './routes/admin.js';
import { requireAdmin } from './adminAuth.js';
import { betaSignup } from './routes/beta.js';
import { createDevice, listDevices } from './routes/devices.js';

const router = Router();
router.use(securityHeaders);

router.get('/configs',     listConfigs);
router.get('/configs/:id/graph', getConfigGraphRoute);
router.post('/graph/clusters', bodySizeLimit, communityWriteRateLimiter, postConfigGraphCluster);
router.patch('/graph/clusters/:id', bodySizeLimit, communityWriteRateLimiter, patchConfigGraphCluster);
router.get('/configs/:id', getConfig);
router.post('/configs',    bodySizeLimit, uploadRateLimiter, uploadConfig);
router.get('/devices', listDevices);
router.post('/devices', bodySizeLimit, communityWriteRateLimiter, createDevice);

router.post('/beta-signup', bodySizeLimit, betaRateLimiter, betaSignup);

// Top-level navigation target for the admin page. Cloudflare Access covers
// /api/v1/admin, so a browser navigation here runs the Access login flow and
// sets the CF_Authorization cookie; the redirect target is fixed to avoid an
// open redirect. Authorization is still enforced on the data routes below.
router.get('/admin/login', (req, res) => res.redirect(302, '/admin'));
router.get('/admin/me',           requireAdmin, adminMe);
router.get('/admin/configs',      requireAdmin, adminListConfigs);
router.get('/admin/configs/:id',  requireAdmin, adminGetConfig);
router.patch('/admin/configs/:id', requireAdmin, bodySizeLimit, adminPatchConfig);
router.delete('/admin/configs/:id', requireAdmin, adminDeleteConfig);

export default router;
