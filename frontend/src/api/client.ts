import axios, { type AxiosResponse } from 'axios';

export const catalogApi = axios.create({
  baseURL: '',
});

export const orderApi = axios.create({
  baseURL: '',
});

function attachToken(config: any) {
  const token = localStorage.getItem('orderflow_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}

// Safety net: an API call should never receive a web page. If it does (e.g. a routing
// mistake), treat it as "not found" instead of letting the page crash on missing fields.
function rejectHtml(response: AxiosResponse) {
  const contentType = String(response.headers?.['content-type'] ?? '');
  if (contentType.includes('text/html')) {
    return Promise.reject(
      Object.assign(new Error('Expected JSON from the API but received HTML'), {
        response: { ...response, status: 404 },
      })
    );
  }
  return response;
}

catalogApi.interceptors.request.use(attachToken);
orderApi.interceptors.request.use(attachToken);
catalogApi.interceptors.response.use(rejectHtml);
orderApi.interceptors.response.use(rejectHtml);
