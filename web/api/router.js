import { Router } from 'express';
import { uploadRateLimiter, bodySizeLimit, securityHeaders } from './middleware.js';
import { listConfigs, getConfig, uploadConfig } from './routes/configs.js';

const router = Router();
router.use(securityHeaders);
router.get('/configs',     listConfigs);
router.get('/configs/:id', getConfig);
router.post('/configs',    bodySizeLimit, uploadRateLimiter, uploadConfig);
export default router;
