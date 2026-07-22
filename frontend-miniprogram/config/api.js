const config = {
  apiBase: 'http://127.0.0.1:8080/api/v1',
  
  timeout: 10000,
  
  statusCode: {
    SUCCESS: 2000,
    UNAUTHORIZED: 4001,
    FORBIDDEN: 4002,
    NOT_FOUND: 4003,
    ERROR: 5000
  }
}

module.exports = config
