module.exports = function (api) {
  const isTest = api.env('test');
  if (isTest) {
    return {
      presets: [
        ['@babel/preset-env', { targets: { node: 'current' } }],
        ['@babel/preset-react', { runtime: 'automatic' }],
        '@babel/preset-typescript',
      ],
    };
  }
  return {
    presets: ['babel-preset-expo'],
  };
};
