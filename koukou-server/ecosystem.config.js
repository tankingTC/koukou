module.exports = {
  apps: [{
    name: 'koukou-server',
    script: 'src/index.js',
    instances: 1,
    exec_mode: 'fork',
    env_production: {
      NODE_ENV: 'production',
      PORT: 8080
    },
    max_memory_restart: '512M',
    error_file: '/var/log/koukou-server/error.log',
    out_file: '/var/log/koukou-server/out.log',
    time: true
  }]
};
