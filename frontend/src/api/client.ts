import axios from 'axios';

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

catalogApi.interceptors.request.use(attachToken);
orderApi.interceptors.request.use(attachToken);
