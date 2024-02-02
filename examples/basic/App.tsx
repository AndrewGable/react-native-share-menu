import React, {useState, useEffect, useCallback, useMemo} from 'react';
import {StyleSheet, Text, View, Image} from 'react-native';
import ShareMenu from 'react-native-share-menu';

const App = () => {
  const [sharedData, setSharedData] = useState('');
  const [sharedMimeType, setSharedMimeType] = useState('');
  const [sharedExtraData, setSharedExtraData] = useState(null);

  const handleShare = useCallback(item => {
    if (!item) {
      return;
    }

    var data,
      extraData,
      mimeType = '';

    if (item?.mimeType) {
      data = item.data;
      mimeType = item.mimeType;
      extraData = item.extraData;
      // Currently, iOS has an extra "data" key, whose value is an array that
      // can have multiple objects in it. Here we're just looking at the first.
    } else if (item.data?.[0]) {
      const firstItem = item.data[0];
      if (firstItem?.mimeType) {
        data = firstItem.data;
        mimeType = firstItem.mimeType;
        extraData = firstItem.extraData;
      }
    }

    setSharedData(data);
    setSharedExtraData(extraData);
    setSharedMimeType(mimeType);
  }, []);

  useEffect(() => {
    ShareMenu.getInitialShare(handleShare);
    // Exhaustive deps don't produce desired behavior, this should only be used on app load
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const isImage: boolean = useMemo(() => {
    const result = !!sharedMimeType && sharedMimeType.startsWith('image/');
    return result;
  }, [sharedMimeType]);

  const isFile: boolean = useMemo(() => {
    return !!sharedMimeType && sharedMimeType !== 'text/plain' && !isImage;
  }, [sharedMimeType, isImage]);

  useEffect(() => {
    const listener = ShareMenu.addNewShareListener(handleShare);

    return () => {
      listener.remove();
    };
    // the handleShare function never changes, we don't need to add it here
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <View style={styles.container}>
      <Text style={styles.welcome}>React Native Share Menu</Text>
      <Text style={styles.instructions}>Shared type: {sharedMimeType}</Text>
      <Text style={styles.instructions}>
        Shared text: {sharedMimeType === 'text/plain' ? sharedData : ''}
      </Text>
      <Text style={styles.instructions}>Shared image:</Text>
      {isImage && (
        <Image
          style={styles.image}
          source={{uri: sharedData}}
          resizeMode="contain"
        />
      )}
      <Text style={styles.instructions}>
        Shared file: {isFile ? sharedData : ''}
      </Text>
      <Text style={styles.instructions}>
        Extra data: {sharedExtraData ? JSON.stringify(sharedExtraData) : ''}
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  welcome: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  instructions: {
    textAlign: 'center',
    color: '#333333',
    marginBottom: 5,
  },
  image: {
    width: '100%',
    height: 200,
  },
});

export default App;
